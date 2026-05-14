FROM ubuntu:26.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    build-essential \
    ca-certificates \
    coreutils \
    curl \
    file \
    findutils \
    fuse3 \
    gawk \
    git \
    jq \
    yq \
    httpie \
    netcat-openbsd \
    rsync \
    sed \
    tar \
    perl \
    libfuse3-4 \
    libfuse3-dev \
    nodejs \
    npm \
    openjdk-21-jdk-headless \
    procps \
    python3 \
    python3-pyfuse3 \
    python3-trio \
    python3-pip \
    python3-venv \
    wget \
    ripgrep \
    strace \
    tini \
  && rm -rf /var/lib/apt/lists/*

RUN mkdir -p \
    /arch/backing \
    /arch/mount \
    /arch/policy \
    /arch/workspace \
    /var/log/arch-sandbox \
    /var/lib/arch-sandbox

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY sandbox_daemon.py /usr/local/bin/sandbox_daemon.py
COPY sandbox_policy.py /usr/local/bin/sandbox_policy.py

RUN chmod +x /usr/local/bin/docker-entrypoint.sh /usr/local/bin/sandbox_daemon.py

WORKDIR /arch/workspace

EXPOSE 18080

ENTRYPOINT ["/usr/bin/tini", "-s", "--", "/usr/local/bin/docker-entrypoint.sh"]
CMD ["python3", "/usr/local/bin/sandbox_daemon.py"]
