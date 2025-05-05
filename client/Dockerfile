FROM node:22.15 AS build
WORKDIR /app
COPY . .
RUN npm ci && npm run build --prod

FROM nginx:alpine
COPY --from=build /app/dist/client /usr/share/nginx/html
