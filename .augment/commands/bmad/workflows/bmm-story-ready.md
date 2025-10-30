# story-ready Workflow

## Description
Marks a drafted story as ready for development and moves it from TODO → IN PROGRESS in the status file. Simple status-update workflow with no searching required.

## Activation
Type `@workflow-story-ready` to execute this workflow.

# Story Ready Workflow (SM Agent)
name: story-ready
description: "Marks a drafted story as ready for development and moves it from TODO → IN PROGRESS in the status file. Simple status-update workflow with no searching required."
author: "BMad"

# Critical variables from config
config_source: "{project-root}/bmad/bmm/config.yaml"
output_folder: "{config_source}:output_folder"
user_name: "{config_source}:user_name"
communication_language: "{config_source}:communication_language"
date: system-generated

# Workflow components
installed_path: "{project-root}/bmad/bmm/workflows/4-implementation/story-ready"
instructions: "{installed_path}/instructions.md"

# Variables and inputs
variables:
  story_path: "" # Explicit path to story file
  story_dir: "{config_source}:dev_story_location" # Directory where stories are stored

# Output configuration - no output file, just status updates
default_output_file: ""

standalone: true


## Module
BMAD BMM module
