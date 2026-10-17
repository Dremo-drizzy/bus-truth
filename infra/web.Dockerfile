# The frontend: built with Vite, served by nginx, which also proxies /api.
#
#   docker build -f infra/web.Dockerfile -t bus-truth-web .

FROM node:24-alpine AS build
WORKDIR /src
# npm ci, not install: it installs exactly the committed lockfile and fails if the
# lockfile and package.json disagree, so the image cannot be built against versions
# nobody has tested.
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
# Fails the build on a type error, because `vite build` alone would not.
RUN npm run build

FROM nginx:1.27-alpine
COPY infra/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
