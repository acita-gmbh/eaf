# Step 4: Success Metrics Definition

## MANDATORY EXECUTION RULES (READ FIRST):

- 🛑 NEVER generate content without user input

- 📖 CRITICAL: ALWAYS read the complete step file before taking any action - partial understanding leads to incomplete decisions
- 🔄 CRITICAL: When loading next step with 'C', ensure the entire file is read and understood before proceeding
- ✅ ALWAYS treat this as collaborative discovery between PM peers
- 📋 YOU ARE A FACILITATOR, not a content generator
- 💬 FOCUS on defining measurable success criteria and business objectives
- 🎯 COLLABORATIVE metric definition that connects to user value

## EXECUTION PROTOCOLS:

- 🎯 Show your analysis before taking any action
- ⚠️ Present A/P/C menu after generating success metrics content
- 💾 ONLY save when user chooses C (Continue)
- 📖 Update frontmatter `stepsCompleted: [1, 2, 3, 4]` before loading next step
- 🚫 FORBIDDEN to load next step until C is selected

## COLLABORATION MENUS (A/P/C):

This step will generate content and present choices:

- **A (Advanced Elicitation)**: Use discovery protocols to develop deeper success metric insights
- **P (Party Mode)**: Bring multiple perspectives to validate comprehensive success metrics
- **C (Continue)**: Save the content to the document and proceed to next step

## PROTOCOL INTEGRATION:

- When 'A' selected: Execute {project-root}/.bmad/core/tasks/advanced-elicitation.xml
- When 'P' selected: Execute {project-root}/.bmad/core/workflows/party-mode/workflow.md
- PROTOCOLS always return to this step's A/P/C menu
- User accepts/rejects protocol changes before proceeding

## CONTEXT BOUNDARIES:

- Current document and frontmatter from previous steps are available
- Product vision and target users already defined
- Connect metrics directly to user value and business objectives
- Focus on measurable, actionable success criteria

## YOUR TASK:

Define comprehensive success metrics that include user success, business objectives, and key performance indicators.

## SUCCESS METRICS DISCOVERY SEQUENCE:

### 1. Begin Success Metrics Discovery

Start with user-centered success definition:
"Now that we know who {{project_name}} serves and what problem it solves, let's define what success looks like.

**Success Discovery:**

- How will we know we're succeeding for our users?
- What would make users say 'this was worth it'?
- What metrics show we're creating real value?

Let's start with the user perspective."

### 2. User Success Metrics

Define success from the user's perspective:

#### User Success Questions:

- "What outcome are users trying to achieve?"
- "How will they know the product is working for them?"
- "What's the moment where they realize this is solving their problem?"
- "What behaviors indicate users are getting value?"

#### User Success Exploration:

- Guide from vague to specific metrics
- "Users are happy" → "Users complete [key action] within [timeframe]"
- "Product is useful" → "Users return [frequency] and use [core feature]"
- Focus on outcomes and behaviors, not just satisfaction scores

### 3. Business Objectives

Define business success metrics:

#### Business Success Questions:

- "What does success look like for the business at 3 months? 12 months?"
- "Are we measuring revenue, user growth, engagement, something else?"
- "What business metrics would make you say 'this is working'?"
- "How does this product contribute to broader company goals?"

#### Business Success Categories:

- **Growth Metrics:** User acquisition, market penetration
- **Engagement Metrics:** Usage patterns, retention, satisfaction
- **Financial Metrics:** Revenue, profitability, cost efficiency
- **Strategic Metrics:** Market position, competitive advantage

### 4. Key Performance Indicators

Define specific, measurable KPIs:

#### KPI Development Process:

- Transform objectives into measurable indicators
- Ensure each KPI has a clear measurement method
- Define targets and timeframes where appropriate
- Include leading indicators that predict success

#### KPI Examples:

- User acquisition: "X new users per month"
- Engagement: "Y% of users complete core journey weekly"
- Business impact: "$Z in cost savings or revenue generation"

### 5. Connect Metrics to Strategy

Ensure metrics align with product vision and user needs:

#### Strategic Alignment:

- Connect each metric back to the product vision
- Ensure user success metrics drive business success
- Validate that metrics measure what truly matters
- Avoid vanity metrics that don't drive decisions

### 6. Generate Success Metrics Content

Prepare the content to append to the document:

#### Content Structure:

When saving to document, append these Level 2 and Level 3 sections:

```markdown
## Success Metrics

[Success metrics content based on conversation]

### Business Objectives

[Business objectives content based on conversation, or N/A if not discussed]

### Key Performance Indicators

[Key performance indicators content based on conversation, or N/A if not discussed]
```

### 7. Present Content and Menu

Show the generated metrics content and present choices:
"I've defined success metrics that will help us track whether {{project_name}} is creating real value for users and achieving business objectives.

**Here's what I'll add to the document:**

[Show the complete markdown content from step 6]

**What would you like to do?**
[A] Advanced Elicitation - Let's dive deeper into these success metrics
[P] Party Mode - Bring different perspectives to validate comprehensive metrics
[C] Continue - Save this to the document and move to next step"

### 8. Handle Menu Selection

#### If 'A' (Advanced Elicitation):

- Execute {project-root}/.bmad/core/tasks/advanced-elicitation.xml with the current metrics content
- Process the enhanced metric insights that come back
- Ask user: "Accept these improvements to the success metrics? (y/n)"
- If yes: Update content with improvements, then return to A/P/C menu
- If no: Keep original content, then return to A/P/C menu

#### If 'P' (Party Mode):

- Execute {project-root}/.bmad/core/workflows/party-mode/workflow.md with the current success metrics
- Process the collaborative metric validation and additional insights
- Ask user: "Accept these changes to the success metrics? (y/n)"
- If yes: Update content with improvements, then return to A/P/C menu
- If no: Keep original content, then return to A/P/C menu

#### If 'C' (Continue):

- Append the final content to `{default_output_file}`
- Update frontmatter: `stepsCompleted: [1, 2, 3, 4]`
- Load `./step-05-scope.md`

## APPEND TO DOCUMENT:

When user selects 'C', append the content directly to the document using the structure from step 6.

## SUCCESS METRICS:

✅ User success metrics that focus on outcomes and behaviors
✅ Clear business objectives aligned with product strategy
✅ Specific, measurable KPIs with defined targets
✅ Metrics that connect user value to business success
✅ A/P/C menu presented and handled correctly
✅ Content properly appended to document when C selected

## FAILURE MODES:

❌ Vague success metrics that can't be measured or tracked
❌ Business objectives disconnected from user success
❌ Too many metrics or missing critical success indicators
❌ Metrics that don't drive actionable decisions
❌ Not presenting A/P/C menu after content generation
❌ Appending content without user selecting 'C'

❌ **CRITICAL**: Reading only partial step file - leads to incomplete understanding and poor decisions
❌ **CRITICAL**: Proceeding with 'C' without fully reading and understanding the next step file
❌ **CRITICAL**: Making decisions without complete understanding of step requirements and protocols

## METRIC QUALITY CRITERIA:

**Good Metrics:**

- Specific and measurable
- Connected to user value or business outcomes
- Actionable - they inform decisions
- Leading indicators when possible
- Easy to track and understand

**Avoid:**

- Vanity metrics that look good but don't drive decisions
- Metrics without clear measurement methods
- Too many competing priorities
- Metrics disconnected from product strategy

## OPTIONAL SECTIONS:

Business Objectives and KPIs sections are optional - include them if the discussion yields meaningful, specific objectives and indicators. If not, focus on the core user success metrics section.

## NEXT STEP:

After user selects 'C' and content is saved to document, load `./step-05-scope.md` to define MVP scope.

Remember: Do NOT proceed to step-05 until user explicitly selects 'C' from the A/P/C menu and content is saved!
