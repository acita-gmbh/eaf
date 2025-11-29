# BMM Workflows

## Available Workflows in bmm

**create-epics-and-stories**
- Path: `.bmad/bmm/workflows/3-solutioning/create-epics-and-stories/workflow.yaml`
- Transform PRD requirements and Architecture decisions into comprehensive stories organized by user value. This workflow requires completed PRD + Architecture documents (UX recommended if UI exists) and breaks down requirements into implementation-ready epics and user stories that incorporate all available technical and design context. Creates detailed, actionable stories with complete acceptance criteria for development teams.

**implementation-readiness**
- Path: `.bmad/bmm/workflows/3-solutioning/implementation-readiness/workflow.yaml`
- Validate that PRD, UX Design, Architecture, Epics and Stories are complete and aligned before Phase 4 implementation. Ensures all artifacts cover the MVP requirements with no gaps or contradictions.

**code-review**
- Path: `.bmad/bmm/workflows/4-implementation/code-review/workflow.yaml`
- Perform an ADVERSARIAL Senior Developer code review that finds 3-10 specific problems in every story. Challenges everything: code quality, test coverage, architecture compliance, security, performance. NEVER accepts 'looks good' - must find minimum issues and can auto-fix with user approval.

**correct-course**
- Path: `.bmad/bmm/workflows/4-implementation/correct-course/workflow.yaml`
- Navigate significant changes during sprint execution by analyzing impact, proposing solutions, and routing for implementation

**create-story**
- Path: `.bmad/bmm/workflows/4-implementation/create-story/workflow.yaml`
- Create the next user story from epics+stories with enhanced context analysis and direct ready-for-dev marking

**dev-story**
- Path: `.bmad/bmm/workflows/4-implementation/dev-story/workflow.yaml`
- Execute a story by implementing tasks/subtasks, writing tests, validating, and updating the story file per acceptance criteria

**retrospective**
- Path: `.bmad/bmm/workflows/4-implementation/retrospective/workflow.yaml`
- Run after epic completion to review overall success, extract lessons learned, and explore if new information emerged that might impact the next epic

**sprint-planning**
- Path: `.bmad/bmm/workflows/4-implementation/sprint-planning/workflow.yaml`
- Generate and manage the sprint status tracking file for Phase 4 implementation, extracting all epics and stories from epic files and tracking their status through the development lifecycle

**create-tech-spec**
- Path: `.bmad/bmm/workflows/bmad-quick-flow/create-tech-spec/workflow.yaml`
- Conversational spec engineering - ask questions, investigate code, produce implementation-ready tech-spec.

**quick-dev**
- Path: `.bmad/bmm/workflows/bmad-quick-flow/quick-dev/workflow.yaml`
- Flexible development - execute tech-specs OR direct instructions with optional planning.

**create-excalidraw-dataflow**
- Path: `.bmad/bmm/workflows/diagrams/create-dataflow/workflow.yaml`
- Create data flow diagrams (DFD) in Excalidraw format

**create-excalidraw-diagram**
- Path: `.bmad/bmm/workflows/diagrams/create-diagram/workflow.yaml`
- Create system architecture diagrams, ERDs, UML diagrams, or general technical diagrams in Excalidraw format

**create-excalidraw-flowchart**
- Path: `.bmad/bmm/workflows/diagrams/create-flowchart/workflow.yaml`
- Create a flowchart visualization in Excalidraw format for processes, pipelines, or logic flows

**create-excalidraw-wireframe**
- Path: `.bmad/bmm/workflows/diagrams/create-wireframe/workflow.yaml`
- Create website or app wireframes in Excalidraw format

**document-project**
- Path: `.bmad/bmm/workflows/document-project/workflow.yaml`
- Analyzes and documents brownfield projects by scanning codebase, architecture, and patterns to create comprehensive reference documentation for AI-assisted development

**workflow-init**
- Path: `.bmad/bmm/workflows/workflow-status/init/workflow.yaml`
- Initialize a new BMM project by determining level, type, and creating workflow path

**workflow-status**
- Path: `.bmad/bmm/workflows/workflow-status/workflow.yaml`
- Lightweight status checker - answers "what should I do now?" for any agent. Reads YAML status file for workflow tracking. Use workflow-init for new projects.


## Execution

When running any workflow:
1. LOAD {project-root}/.bmad/core/tasks/workflow.xml
2. Pass the workflow path as 'workflow-config' parameter
3. Follow workflow.xml instructions EXACTLY
4. Save outputs after EACH section

## Modes
- Normal: Full interaction
- #yolo: Skip optional steps
