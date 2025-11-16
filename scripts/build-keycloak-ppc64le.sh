#!/usr/bin/env bash

# ==============================================================================
# Build Script for Keycloak ppc64le Docker Image
# ==============================================================================
#
# Purpose: Automates building custom Keycloak Docker image for ppc64le architecture
# Author: EAF Team
# Version: 1.0.0
# Date: 2025-11-16
#
# Usage:
#   ./scripts/build-keycloak-ppc64le.sh [OPTIONS]
#
# Options:
#   --version VERSION    Keycloak version to build (default: 26.4.2)
#   --registry REGISTRY  Container registry URL (default: docker.io)
#   --tag TAG            Image tag (default: keycloak:VERSION-ppc64le)
#   --push               Push image to registry after successful build
#   --dry-run            Show commands without executing
#   --help               Display this help message
#
# Prerequisites:
#   - Docker with buildx support
#   - QEMU ppc64le emulation (for testing on non-ppc64le hosts)
#   - Registry authentication (if using --push)
#
# Examples:
#   # Build locally
#   ./scripts/build-keycloak-ppc64le.sh
#
#   # Build and push to GitHub Container Registry
#   ./scripts/build-keycloak-ppc64le.sh --registry ghcr.io/acita-gmbh --push
#
#   # Build specific version for custom registry
#   ./scripts/build-keycloak-ppc64le.sh --version 26.4.3 --registry localhost:5000 --tag keycloak:test-ppc64le --push
#
# ==============================================================================

set -euo pipefail

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Default values
DEFAULT_VERSION="26.4.2"
DEFAULT_REGISTRY="docker.io"
DRY_RUN=false
PUSH=false

# Script variables
KEYCLOAK_VERSION="${DEFAULT_VERSION}"
REGISTRY="${DEFAULT_REGISTRY}"
TAG=""
DOCKERFILE="docker/keycloak/Dockerfile.ppc64le"
BUILD_CONTEXT="."

# ==============================================================================
# Functions
# ==============================================================================

log_info() {
    echo -e "${BLUE}ℹ️  $*${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $*${NC}"
}

log_warn() {
    echo -e "${YELLOW}⚠️  $*${NC}"
}

log_error() {
    echo -e "${RED}❌ $*${NC}" >&2
}

show_help() {
    grep '^#' "$0" | grep -v '#!/usr/bin/env' | sed 's/^# //' | sed 's/^#//'
}

check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check Docker
    if ! command -v docker &> /dev/null
    then
        log_error "Docker is not installed or not in PATH"
        exit 1
    fi
    log_success "Docker found: $(docker --version)"

    # Check Docker buildx
    if ! docker buildx version &> /dev/null
    then
        log_error "Docker buildx is not available"
        log_info "Install with: docker buildx create --use"
        exit 1
    fi
    log_success "Docker buildx found: $(docker buildx version)"

    # Check Dockerfile exists
    if [[ ! -f "${DOCKERFILE}" ]]
    then
        log_error "Dockerfile not found: ${DOCKERFILE}"
        exit 1
    fi
    log_success "Dockerfile found: ${DOCKERFILE}"

    # Check QEMU support for ppc64le
    if ! docker buildx inspect | grep -q "linux/ppc64le"
    then
        log_warn "ppc64le platform not detected in buildx"
        log_info "Setting up QEMU emulation..."
        docker run --rm --privileged multiarch/qemu-user-static --reset -p yes || true
    fi
    log_success "ppc64le platform support confirmed"
}

build_image() {
    local image_tag="${1}"

    log_info "Building Keycloak ${KEYCLOAK_VERSION} for ppc64le architecture..."
    log_info "Image tag: ${image_tag}"

    local build_cmd=(
        docker buildx build
        --platform linux/ppc64le
        --build-arg "KEYCLOAK_VERSION=${KEYCLOAK_VERSION}"
        -f "${DOCKERFILE}"
        -t "${image_tag}"
        --load
        "${BUILD_CONTEXT}"
    )

    if [[ "${DRY_RUN}" == "true" ]]
    then
        log_info "DRY RUN: ${build_cmd[*]}"
        return 0
    fi

    log_info "Executing build command..."
    echo ""

    if "${build_cmd[@]}"
    then
        log_success "Build completed successfully"

        # Show image info
        echo ""
        log_info "Image details:"
        docker images "${image_tag}"

        # Show image size
        local size
        size=$(docker images "${image_tag}" --format "{{.Size}}")
        log_info "Image size: ${size}"

        if [[ "${size}" == *"GB"* ]]
        then
            log_warn "Image size exceeds 1GB - consider optimization"
        fi
    else
        log_error "Build failed"
        exit 1
    fi
}

push_image() {
    local image_tag="${1}"

    log_info "Pushing image to registry: ${image_tag}"

    if [[ "${DRY_RUN}" == "true" ]]
    then
        log_info "DRY RUN: docker push ${image_tag}"
        return 0
    fi

    if docker push "${image_tag}"
    then
        log_success "Image pushed successfully"
    else
        log_error "Push failed - check registry authentication"
        exit 1
    fi
}

verify_build() {
    local image_tag="${1}"

    log_info "Verifying build..."

    if [[ "${DRY_RUN}" == "true" ]]
    then
        log_info "DRY RUN: Skipping verification"
        return 0
    fi

    # Check if image exists
    if ! docker images "${image_tag}" --format "{{.Repository}}" | grep -q "${image_tag%%:*}"
    then
        log_error "Image not found after build: ${image_tag}"
        exit 1
    fi

    log_success "Build verification passed"
}

# ==============================================================================
# Main Script
# ==============================================================================

main() {
    # Parse command-line arguments
    while [[ $# -gt 0 ]]
    do
        case $1 in
            --version)
                KEYCLOAK_VERSION="$2"
                shift 2
                ;;
            --registry)
                REGISTRY="$2"
                shift 2
                ;;
            --tag)
                TAG="$2"
                shift 2
                ;;
            --push)
                PUSH=true
                shift
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --help)
                show_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                echo ""
                show_help
                exit 1
                ;;
        esac
    done

    # Set default tag if not provided
    if [[ -z "${TAG}" ]]
    then
        if [[ "${REGISTRY}" == "docker.io" ]]
        then
            TAG="keycloak:${KEYCLOAK_VERSION}-ppc64le"
        else
            TAG="${REGISTRY}/keycloak:${KEYCLOAK_VERSION}-ppc64le"
        fi
    elif [[ ! "${TAG}" =~ : ]] && [[ "${REGISTRY}" != "docker.io" ]]
    then
        # Add registry prefix if tag doesn't contain : and registry is set
        TAG="${REGISTRY}/${TAG}"
    fi

    # Show configuration
    echo ""
    log_info "Build Configuration:"
    echo "  Keycloak Version: ${KEYCLOAK_VERSION}"
    echo "  Registry:         ${REGISTRY}"
    echo "  Image Tag:        ${TAG}"
    echo "  Dockerfile:       ${DOCKERFILE}"
    echo "  Push to Registry: ${PUSH}"
    echo "  Dry Run:          ${DRY_RUN}"
    echo ""

    # Check prerequisites
    check_prerequisites
    echo ""

    # Build image
    build_image "${TAG}"
    echo ""

    # Verify build
    verify_build "${TAG}"
    echo ""

    # Push if requested
    if [[ "${PUSH}" == "true" ]]
    then
        push_image "${TAG}"
        echo ""
    fi

    # Summary
    echo ""
    log_success "========================="
    log_success "Build process complete!"
    log_success "========================="
    echo ""
    log_info "Image tag: ${TAG}"

    if [[ "${PUSH}" == "true" ]] && [[ "${DRY_RUN}" == "false" ]]
    then
        log_info "Image is available in registry: ${REGISTRY}"
    else
        log_info "Image is available locally (not pushed to registry)"
    fi

    echo ""
    log_info "Next steps:"
    echo "  1. Test image: docker run --platform linux/ppc64le -p 8080:8080 ${TAG} start-dev"
    echo "  2. Verify health: curl http://localhost:8080/health"
    echo "  3. Use in docker-compose: Update docker-compose.ppc64le.yml with image: ${TAG}"
    echo ""
}

# Run main function
main "$@"
