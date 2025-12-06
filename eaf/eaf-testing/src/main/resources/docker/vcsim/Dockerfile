# VCSIM (vCenter Simulator) Multi-Architecture Dockerfile
#
# This Dockerfile builds VCSIM from govmomi source for native architecture support.
# Used by VcsimContainer on ARM64 (Apple Silicon) where the official vmware/vcsim
# image (AMD64-only) fails under QEMU emulation.
#
# Build args:
#   GO_VERSION    - Go toolchain version (default: 1.23)
#   VCSIM_VERSION - govmomi tag to build (default: v0.47.0)
#
# Usage:
#   docker build -t eaf-vcsim:v0.47.0 \
#     --build-arg VCSIM_VERSION=v0.47.0 \
#     -f Dockerfile .

ARG GO_VERSION=1.23
ARG VCSIM_VERSION=v0.47.0

# =============================================================================
# Stage 1: Build VCSIM binary
# =============================================================================
FROM golang:${GO_VERSION}-alpine AS builder

# Re-declare ARG after FROM (ARGs are scoped to build stage)
ARG VCSIM_VERSION

WORKDIR /build

# Install git (required for cloning repository)
RUN apk add --no-cache git ca-certificates

# Create non-root user for runtime stage
RUN adduser -D -u 10001 appuser

# Create /tmp directory for scratch base (VCSIM needs it for runtime files)
RUN mkdir -p /tmp/vcsim && chown -R appuser:appuser /tmp

# Clone govmomi at specific version (shallow clone for speed)
RUN git clone --depth 1 --branch ${VCSIM_VERSION} \
    https://github.com/vmware/govmomi.git .

# Build VCSIM binary
# CGO_ENABLED=0 creates a statically-linked binary (works with scratch base)
# -ldflags='-s -w' strips debug info for smaller binary (~30% reduction)
WORKDIR /build/vcsim
RUN CGO_ENABLED=0 GOOS=linux go build \
    -ldflags='-s -w' \
    -o /vcsim .

# =============================================================================
# Stage 2: Minimal runtime image
# =============================================================================
FROM scratch

# Copy CA certificates for HTTPS connections
COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/

# Copy user/group for non-root execution
COPY --from=builder /etc/passwd /etc/passwd
COPY --from=builder /etc/group /etc/group

# Copy /tmp directory (VCSIM needs it for runtime files and mounted TLS certs)
COPY --from=builder --chown=appuser:appuser /tmp /tmp

# Copy VCSIM binary
COPY --from=builder /vcsim /vcsim

# Run as non-root user (security best practice)
USER appuser

# VCSIM default port
EXPOSE 8989

# Start VCSIM listening on all interfaces
# Additional flags (e.g., -tlscert, -tlskey) passed via container command
ENTRYPOINT ["/vcsim"]
CMD ["-l", "0.0.0.0:8989"]
