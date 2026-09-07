#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <future>
#include <limits>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#include "napi/native_api.h"
#include "clogan_core.h"
#include "clogan_status.h"

namespace {

enum class CommandType { INIT, WRITE, FLUSH, REOPEN, DEBUG, STOP };

struct Command {
    CommandType type = CommandType::WRITE;
    std::string cachePath;
    std::string logPath;
    std::string date;
    std::string log;
    std::string threadName;
    std::string key;
    std::string iv;
    int maxFileSize = 0;
    int logType = 0;
    long long timestamp = 0;
    long long threadId = 0;
    int isMainThread = 0;
    bool debug = false;
    std::shared_ptr<std::promise<int>> completion;
};

class LoganWorker final {
public:
    static LoganWorker &Instance()
    {
        static LoganWorker instance;
        return instance;
    }

    int Init(Command command, size_t maxQueue)
    {
        std::lock_guard<std::mutex> lifecycleLock(lifecycleMutex_);
        if (initialized_.load()) {
            return initStatus_.load();
        }
        maxQueue_ = maxQueue;
        if (!thread_.joinable()) {
            thread_ = std::thread(&LoganWorker::Run, this);
        }
        return Execute(std::move(command));
    }

    bool Write(Command command)
    {
        if (!initialized_.load()) {
            return false;
        }
        std::lock_guard<std::mutex> lock(mutex_);
        if (writeCount_ >= maxQueue_) {
            ++dropped_;
            return false;
        }
        command.type = CommandType::WRITE;
        queue_.push_back(std::move(command));
        ++writeCount_;
        condition_.notify_one();
        return true;
    }

    int Flush()
    {
        Command command;
        command.type = CommandType::FLUSH;
        return Execute(std::move(command));
    }

    int Reopen(std::string date)
    {
        Command command;
        command.type = CommandType::REOPEN;
        command.date = std::move(date);
        return Execute(std::move(command));
    }

    int SetDebug(bool debug)
    {
        Command command;
        command.type = CommandType::DEBUG;
        command.debug = debug;
        return Execute(std::move(command));
    }

    bool IsInitialized() const
    {
        return initialized_.load();
    }

    size_t Pending() const
    {
        std::lock_guard<std::mutex> lock(mutex_);
        return queue_.size();
    }

    uint64_t Dropped() const
    {
        return dropped_.load();
    }

    int LastStatus() const
    {
        return lastStatus_.load();
    }

    std::string CurrentDate() const
    {
        std::lock_guard<std::mutex> lock(stateMutex_);
        return currentDate_;
    }

    ~LoganWorker()
    {
        if (!thread_.joinable()) {
            return;
        }
        Command command;
        command.type = CommandType::STOP;
        Execute(std::move(command));
        thread_.join();
    }

private:
    LoganWorker() = default;
    LoganWorker(const LoganWorker &) = delete;
    LoganWorker &operator=(const LoganWorker &) = delete;

    int Execute(Command command)
    {
        if (!thread_.joinable()) {
            return CLOGAN_FLUSH_FAIL_INIT;
        }
        auto completion = std::make_shared<std::promise<int>>();
        auto result = completion->get_future();
        command.completion = completion;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            queue_.push_back(std::move(command));
        }
        condition_.notify_one();
        return result.get();
    }

    void Complete(Command &command, int status)
    {
        lastStatus_.store(status);
        if (command.completion) {
            command.completion->set_value(status);
        }
    }

    void Run()
    {
        while (true) {
            Command command;
            {
                std::unique_lock<std::mutex> lock(mutex_);
                condition_.wait(lock, [this] { return !queue_.empty(); });
                command = std::move(queue_.front());
                queue_.pop_front();
                if (command.type == CommandType::WRITE) {
                    --writeCount_;
                }
            }

            if (command.type == CommandType::STOP) {
                int status = initialized_.load() ? clogan_flush() : CLOGAN_FLUSH_FAIL_INIT;
                Complete(command, status);
                return;
            }

            int status = CLOGAN_FLUSH_FAIL_INIT;
            switch (command.type) {
                case CommandType::INIT:
                    status = clogan_init(command.cachePath.c_str(), command.logPath.c_str(),
                        command.maxFileSize, command.key.data(), command.iv.data());
                    initStatus_.store(status);
                    clogan_debug(command.debug ? 1 : 0);
                    initialized_.store(status == CLOGAN_INIT_SUCCESS_MMAP ||
                        status == CLOGAN_INIT_SUCCESS_MEMORY);
                    break;
                case CommandType::WRITE:
                    if (!initialized_.load()) {
                        break;
                    }
                    {
                        std::lock_guard<std::mutex> lock(stateMutex_);
                        if (currentDate_ != command.date) {
                            if (!currentDate_.empty()) {
                                clogan_flush();
                            }
                            status = clogan_open(command.date.c_str());
                            if (status != CLOGAN_OPEN_SUCCESS) {
                                break;
                            }
                            currentDate_ = command.date;
                        }
                    }
                    status = clogan_write(command.logType, command.log.data(), command.timestamp,
                        command.threadName.data(), command.threadId, command.isMainThread);
                    break;
                case CommandType::FLUSH:
                    if (initialized_.load()) {
                        status = clogan_flush();
                    }
                    break;
                case CommandType::REOPEN:
                    if (initialized_.load()) {
                        clogan_flush();
                        status = clogan_open(command.date.c_str());
                        if (status == CLOGAN_OPEN_SUCCESS) {
                            std::lock_guard<std::mutex> lock(stateMutex_);
                            currentDate_ = command.date;
                        }
                    }
                    break;
                case CommandType::DEBUG:
                    clogan_debug(command.debug ? 1 : 0);
                    status = 0;
                    break;
                case CommandType::STOP:
                    break;
            }
            Complete(command, status);
        }
    }

    mutable std::mutex mutex_;
    mutable std::mutex stateMutex_;
    std::mutex lifecycleMutex_;
    std::condition_variable condition_;
    std::deque<Command> queue_;
    std::thread thread_;
    size_t maxQueue_ = 500;
    size_t writeCount_ = 0;
    std::atomic<bool> initialized_ {false};
    std::atomic<uint64_t> dropped_ {0};
    std::atomic<int> initStatus_ {CLOGAN_FLUSH_FAIL_INIT};
    std::atomic<int> lastStatus_ {CLOGAN_FLUSH_FAIL_INIT};
    std::string currentDate_;
};

bool GetString(napi_env env, napi_value value, std::string &result)
{
    napi_valuetype type;
    if (napi_typeof(env, value, &type) != napi_ok || type != napi_string) {
        return false;
    }
    size_t length = 0;
    if (napi_get_value_string_utf8(env, value, nullptr, 0, &length) != napi_ok) {
        return false;
    }
    result.resize(length + 1);
    size_t copied = 0;
    if (napi_get_value_string_utf8(env, value, result.data(), result.size(), &copied) != napi_ok) {
        return false;
    }
    result.resize(copied);
    return true;
}

bool GetInt32(napi_env env, napi_value value, int32_t &result)
{
    return napi_get_value_int32(env, value, &result) == napi_ok;
}

bool GetInt64(napi_env env, napi_value value, long long &result)
{
    double number = 0;
    if (napi_get_value_double(env, value, &number) != napi_ok || number < 0 ||
        number > static_cast<double>(std::numeric_limits<long long>::max())) {
        return false;
    }
    result = static_cast<long long>(number);
    return true;
}

bool GetBool(napi_env env, napi_value value, bool &result)
{
    return napi_get_value_bool(env, value, &result) == napi_ok;
}

bool GetKey(napi_env env, napi_value value, std::string &result)
{
    bool isTypedArray = false;
    if (napi_is_typedarray(env, value, &isTypedArray) != napi_ok || !isTypedArray) {
        return false;
    }
    napi_typedarray_type type;
    size_t length = 0;
    void *data = nullptr;
    napi_value arrayBuffer;
    size_t byteOffset = 0;
    if (napi_get_typedarray_info(env, value, &type, &length, &data, &arrayBuffer, &byteOffset) != napi_ok ||
        type != napi_uint8_array || length != 16 || data == nullptr) {
        return false;
    }
    result.assign(static_cast<const char *>(data), length);
    return true;
}

napi_value ThrowTypeError(napi_env env, const char *message)
{
    napi_throw_type_error(env, nullptr, message);
    return nullptr;
}

napi_value InitNative(napi_env env, napi_callback_info info)
{
    size_t argc = 7;
    napi_value args[7] = {nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc != 7) {
        return ThrowTypeError(env, "init expects 7 arguments");
    }
    Command command;
    command.type = CommandType::INIT;
    int32_t maxFileSize = 0;
    int32_t maxQueue = 0;
    if (!GetString(env, args[0], command.cachePath) || command.cachePath.empty() ||
        !GetString(env, args[1], command.logPath) || command.logPath.empty() ||
        !GetInt32(env, args[2], maxFileSize) || maxFileSize <= 0 ||
        !GetKey(env, args[3], command.key) || !GetKey(env, args[4], command.iv) ||
        !GetInt32(env, args[5], maxQueue) || maxQueue <= 0 ||
        !GetBool(env, args[6], command.debug)) {
        return ThrowTypeError(env, "invalid Logan configuration");
    }
    command.maxFileSize = maxFileSize;
    int status = LoganWorker::Instance().Init(std::move(command), static_cast<size_t>(maxQueue));
    napi_value result;
    napi_create_int32(env, status, &result);
    return result;
}

napi_value WriteNative(napi_env env, napi_callback_info info)
{
    size_t argc = 7;
    napi_value args[7] = {nullptr};
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc != 7) {
        return ThrowTypeError(env, "write expects 7 arguments");
    }
    Command command;
    int32_t type = 0;
    bool isMain = false;
    if (!GetString(env, args[0], command.date) || command.date.empty() ||
        !GetInt32(env, args[1], type) || !GetString(env, args[2], command.log) || command.log.empty() ||
        !GetInt64(env, args[3], command.timestamp) || !GetString(env, args[4], command.threadName) ||
        !GetInt64(env, args[5], command.threadId) || !GetBool(env, args[6], isMain)) {
        return ThrowTypeError(env, "invalid log entry");
    }
    command.logType = type;
    command.isMainThread = isMain ? 1 : 0;
    bool accepted = LoganWorker::Instance().Write(std::move(command));
    napi_value result;
    napi_get_boolean(env, accepted, &result);
    return result;
}

napi_value FlushNative(napi_env env, napi_callback_info info)
{
    int status = LoganWorker::Instance().Flush();
    napi_value result;
    napi_create_int32(env, status, &result);
    return result;
}

napi_value ReopenNative(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value arg = nullptr;
    napi_get_cb_info(env, info, &argc, &arg, nullptr, nullptr);
    std::string date;
    if (argc != 1 || !GetString(env, arg, date) || date.empty()) {
        return ThrowTypeError(env, "reopen expects a date");
    }
    int status = LoganWorker::Instance().Reopen(std::move(date));
    napi_value result;
    napi_create_int32(env, status, &result);
    return result;
}

napi_value DebugNative(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value arg = nullptr;
    napi_get_cb_info(env, info, &argc, &arg, nullptr, nullptr);
    bool debug = false;
    if (argc != 1 || !GetBool(env, arg, debug)) {
        return ThrowTypeError(env, "setDebug expects a boolean");
    }
    int status = LoganWorker::Instance().SetDebug(debug);
    napi_value result;
    napi_create_int32(env, status, &result);
    return result;
}

void SetNamedBoolean(napi_env env, napi_value object, const char *name, bool value)
{
    napi_value property;
    napi_get_boolean(env, value, &property);
    napi_set_named_property(env, object, name, property);
}

void SetNamedInt64(napi_env env, napi_value object, const char *name, int64_t value)
{
    napi_value property;
    napi_create_int64(env, value, &property);
    napi_set_named_property(env, object, name, property);
}

void SetNamedString(napi_env env, napi_value object, const char *name, const std::string &value)
{
    napi_value property;
    napi_create_string_utf8(env, value.c_str(), value.size(), &property);
    napi_set_named_property(env, object, name, property);
}

napi_value StateNative(napi_env env, napi_callback_info info)
{
    auto &worker = LoganWorker::Instance();
    napi_value result;
    napi_create_object(env, &result);
    SetNamedBoolean(env, result, "initialized", worker.IsInitialized());
    SetNamedInt64(env, result, "pending", static_cast<int64_t>(worker.Pending()));
    SetNamedInt64(env, result, "dropped", static_cast<int64_t>(worker.Dropped()));
    SetNamedInt64(env, result, "lastStatus", worker.LastStatus());
    SetNamedString(env, result, "currentDate", worker.CurrentDate());
    return result;
}

napi_value Export(napi_env env, napi_value exports)
{
    napi_property_descriptor descriptors[] = {
        {"init", nullptr, InitNative, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"write", nullptr, WriteNative, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"flush", nullptr, FlushNative, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"reopen", nullptr, ReopenNative, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"setDebug", nullptr, DebugNative, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"getState", nullptr, StateNative, nullptr, nullptr, nullptr, napi_default, nullptr},
    };
    napi_define_properties(env, exports, sizeof(descriptors) / sizeof(descriptors[0]), descriptors);
    return exports;
}

} // namespace

static napi_module loganModule = {
    .nm_version = 1,
    .nm_flags = 0,
    .nm_filename = nullptr,
    .nm_register_func = Export,
    .nm_modname = "logan",
    .nm_priv = nullptr,
    .reserved = {nullptr},
};

extern "C" __attribute__((constructor)) void RegisterLoganModule()
{
    napi_module_register(&loganModule);
}
