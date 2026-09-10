FROM node:22-alpine
WORKDIR /app
COPY package.json server.mjs ./
RUN mkdir -p /app/data && chown -R node:node /app
USER node
ENV PORT=8080 ATTEND_PRO_DB_FILE=/app/data/central-data.json
EXPOSE 8080
CMD ["node", "server.mjs"]
