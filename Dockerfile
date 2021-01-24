FROM clojure:lein-2.9.1-alpine

WORKDIR /app

COPY project.clj project.clj
RUN lein deps
COPY ./ ./

CMD ["lein", "run"]