FROM clojure:temurin-21-tools-deps-jammy AS base

RUN mkdir /app
ADD deps.edn /app/deps.edn
ADD src /app/src
RUN cd /app && clojure -P

# gate the build by unit tests succeeding
FROM base AS unit-test-gate
ADD bin /app/bin
ADD test /app/test
ADD .kaocha /app/.kaocha
RUN cd /app && ./bin/kaocha

FROM unit-test-gate AS final
WORKDIR /app
CMD ["clj", "-M", "-m", "viasat.cwmp-cpe.main"]
