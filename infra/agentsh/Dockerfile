FROM ubuntu:24.04

ARG AGENTSH_VERSION=0.16.6
ARG TARGETARCH=amd64

ENV DEBIAN_FRONTEND=noninteractive
ENV AGENTSH_CONFIG=/etc/agentsh/server-config.yaml
ENV AGENTSH_SHIM_FORCE=1

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    jq \
    bash \
    git \
    ripgrep \
    procps \
    python3 \
    python3-pip \
    python3-venv \
    nodejs \
    npm \
    openjdk-25-jdk \
    gradle \
  && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/agentsh.deb \
    "https://github.com/canyonroad/agentsh/releases/download/v${AGENTSH_VERSION}/agentsh_${AGENTSH_VERSION}_linux_${TARGETARCH}.deb" \
  && apt-get update \
  && apt-get install -y /tmp/agentsh.deb \
  && rm -f /tmp/agentsh.deb \
  && rm -rf /var/lib/apt/lists/*

COPY server-config.yaml /etc/agentsh/server-config.yaml
COPY api_keys.yaml /etc/agentsh/api_keys.yaml
COPY policies/default.yaml /etc/agentsh/policies/default.yaml

RUN mkdir -p /var/log/agentsh /var/lib/agentsh

RUN agentsh shim install-shell \
    --root / \
    --shim /usr/bin/agentsh-shell-shim \
    --bash \
    --i-understand-this-modifies-the-host

WORKDIR /workspace

EXPOSE 18080

CMD ["agentsh", "server", "--config", "/etc/agentsh/server-config.yaml"]
