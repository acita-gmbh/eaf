# /party-mode Command

When this command is used, execute the following workflow:

## Party Mode Workflow

# Party Mode - Multi-Agent Group Discussion Workflow
name: "party-mode"
description: "Orchestrates group discussions between all installed BMAD agents, enabling natural multi-agent conversations"
author: "BMad"

# Critical data sources - manifest and config overrides
agent_manifest: "{project-root}/bmad/_cfg/agent-manifest.csv"
agent_overrides: "{project-root}/bmad/_cfg/agents/*.customize.yaml"
date: system-generated

# This is an interactive action workflow - no template output
template: false
instructions: "{project-root}/bmad/core/workflows/party-mode/instructions.md"

# Exit conditions
exit_triggers:
  - "*exit"
  - "end party mode"
  - "stop party mode"

standalone: true

web_bundle: false


## Command Usage

This command executes the Party Mode workflow from the BMAD CORE module.

## Module

Part of the BMAD CORE module.
