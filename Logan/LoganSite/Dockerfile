# build frontend
FROM node:10 as frontend

WORKDIR /web
COPY . .
RUN npm config set registry https://registry.npmmirror.com && \
    npm config set sass_binary_site "https://npmmirror.com/mirrors/node-sass/"  && \
    npm install && \
    npm run build

FROM library/nginx
USER root

# copy static files
COPY --from=frontend /web/build/ /usr/share/nginx/html/

# HEALTHCHECK
HEALTHCHECK --interval=5s --timeout=3s  --retries=3  CMD service nginx status | grep running || exit 1