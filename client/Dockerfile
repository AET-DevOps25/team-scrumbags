FROM node:22.15 as build
WORKDIR /app
COPY . .
RUN npm ci && npm run build --prod

FROM nginx:alpine
COPY --from=build /app/dist/your-angular-app /usr/share/nginx/html
