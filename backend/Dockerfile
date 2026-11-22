# ---------- build: Maven + JDK 26 (Temurin) ----------
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build

# camada de dependências em cache (acelera rebuilds que só mexem em src/)
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# ---------- runtime: JDK 26 (Temurin) ----------
# Nota sobre GraalVM: as imagens públicas do Oracle GraalVM hoje cobrem apenas LTS
# (25), e bytecode compilado com --enable-preview do JDK 26 (JEP 525) exige runtime
# 26 exato. GraalVM JIT entra no projeto via build nativo com o tarball do GraalVM
# para JDK 26 — veja a etapa de Native Image no README.
FROM eclipse-temurin:26-jre AS runtime
WORKDIR /app

# curl apenas para o healthcheck do compose
RUN apt-get update -qq && apt-get install -qq -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 loomlab
USER loomlab

COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080

# a flag de preview é obrigatória em runtime: StructuredTaskScope (JEP 525)
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
