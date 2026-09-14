package com.meituan.logan.web.mapper;

import com.meituan.logan.web.dto.LoganTaskDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LogRetentionMapper {
    List<LoganTaskDTO> expiredNativeTasks(@Param("cutoff") long cutoff,
                                        @Param("afterId") long afterId, @Param("limit") int limit);

    List<Long> expiredWebTasks(@Param("cutoff") long cutoff,
                               @Param("afterId") long afterId, @Param("limit") int limit);

    int deleteNativeTasks(@Param("ids") List<Long> ids, @Param("cutoff") long cutoff);

    int deleteWebTasks(@Param("ids") List<Long> ids, @Param("cutoff") long cutoff);

    int countFileReferences(@Param("fileName") String fileName, @Param("cutoff") long cutoff);

    int deleteOrphanNativeDetails(@Param("limit") int limit);

    int deleteOrphanWebDetails(@Param("limit") int limit);
}
