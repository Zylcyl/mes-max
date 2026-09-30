# 前端镜像：Vite 构建 -> Nginx 托管静态文件并反向代理 /api
FROM node:20-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npx vite build

FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
