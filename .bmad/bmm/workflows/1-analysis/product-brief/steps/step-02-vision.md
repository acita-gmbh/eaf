# Step 2: Product Vision Discovery

## MANDATORY EXECUTION RULES (READ FIRST):

- 🛑 NEVER generate content without user input

- 📖 CRITICAL: ALWAYS read the complete step file before taking any action - partial understanding leads to incomplete decisions
- 🔄 CRITICAL: When loading next step with 'C', ensure the entire file is read and understood before proceeding
- ✅ ALWAYS treat this as collaborative discovery between PM peers
- 📋 YOU ARE A FACILITATOR, not a content generator
- 💬 FOCUS on product vision, problem, and solution discovery only
- 🎯 COLLABORATIVE discovery, not assumption-based vision crafting

## EXECUTION PROTOCOLS:

- 🎯 Show your analysis before taking any action
- ⚠️ Present A/P/C menu after generating executive summary content
- 💾 ONLY save when user chooses C (Continue)
- 📖 Update frontmatter `stepsCompleted: [1, 2]` before loading next step
- 🚫 FORBIDDEN to load next step until C is selected

## COLLABORATION MENUS (A/P/C):

This step will generate content and present choices:

- **A (Advanced Elicitation)**: Use discovery protocols to develop deeper insights about product vision
- **P (Party Mode)**: Bring multiple perspectives to explore product vision and positioning
- **C (Continue)**: Save the content to the document and proceed to next step

## PROTOCOL INTEGRATION:

- When 'A' selected: Execute {project-root}/.bmad/core/tasks/advanced-elicitation.xml
- When 'P' selected: Execute {project-root}/.bmad/core/workflows/party-mode/workflow.md
- PROTOCOLS always return to this step's A/P/C menu
- User accepts/rejects protocol changes before proceeding

## CONTEXT BOUNDARIES:

- Current document and frontmatter from step 1 are available
- Input documents already loaded are in memory
- This will be the first content section appended to the document
- Focus on clear, compelling product vision and problem statement

## YOUR TASK:

Conduct comprehensive product vision discovery to define the core problem, solution, and unique value proposition.

## VISION DISCOVERY SEQUENCE:

### 1. Begin Vision Discovery Conversation

Start with open-ended vision exploration:
"As your PM peer, I'm excited to help you shape the vision for {{project_name}}. Let's start with the foundation.

**Tell me about the product you envision:**

- What core problem are you trying to solve?
- Who experiences this problem most acutely?
- What would success look like for the people you're helping?
- What excites you most about this solution?

Let's start with the problem space before we get into solutions."

### 2. Deep Problem Understanding

Explore the problem from multiple angles:

#### Problem Discovery Questions:

- "How do people currently solve this problem?"
- "What's frustrating about current solutions?"
- "What happens if this problem goes unsolved?"
- "Who feels this pain most intensely?"

#### Impact Exploration:

- "What's the real cost of this problem?"
- "How does it affect people's daily lives or work?"
- "What are the emotional and practical consequences?"
- "Why is solving this important right now?"

### 3. Current Solutions Analysis

Understand the competitive landscape:

- "What solutions exist today?"
- "Where do they fall short?"
- "What gaps are they leaving open?"
- "Why haven't existing solutions solved this completely?"

### 4. Solution Vision

Craft the proposed solution collaboratively:

- "If we could solve this perfectly, what would that look like?"
- "What's the simplest way we could make a meaningful difference?"
- "What makes your approach different from what's out there?"
- "What would make users say 'this is exactly what I needed'?"

### 5. Unique Differentiators

Identify what makes this product special:

- "What's your unfair advantage?"
- "What would be hard for competitors to copy?"
- "What insight or approach is uniquely yours?"
- "Why is now the right time for this solution?"

### 6. Generate Executive Summary Content

Prepare the content to append to the document:

#### Content Structure:

When saving to document, append these Level 2 and Level 3 sections:

```markdown
## Executive Summary

[Executive summary content based on conversation]

---

## Core Vision

### Problem Statement

[Problem statement content based on conversation]

### Problem Impact

[Problem impact content based on conversation]

### Why Existing Solutions Fall Short

[Analysis of existing solution gaps based on conversation]

### Proposed Solution

[Proposed solution description based on conversation]

### Key Differentiators

[Key differentiators based on conversation]
```

### 7. Present Content and Menu

Show the generated content and present choices:
"I've drafted the executive summary and core vision based on our conversation. This captures the essence of {{project_name}} and what makes it special.

**Here's what I'll add to the document:**

[Show the complete markdown content from step 6]

**What would you like to do?**
[A] Advanced Elicitation - Let's dive deeper and refine the product vision
[P] Party Mode - Bring different perspectives to explore positioning and differentiation
[C] Continue - Save this to the document and move to next step"

### 8. Handle Menu Selection

#### If 'A' (Advanced Elicitation):

- Execute {project-root}/.bmad/core/tasks/advanced-elicitation.xml with the current vision content
- Process the enhanced vision insights that come back
- Ask user: "Accept these improvements to the product vision? (y/n)"
- If yes: Update content with improvements, then return to A/P/C menu
- If no: Keep original content, then return to A/P/C menu

#### If 'P' (Party Mode):

- Execute {project-root}/.bmad/core/workflows/party-mode/workflow.md with the current vision content
- Process the collaborative vision exploration and positioning insights
- Ask user: "Accept these changes to the product vision? (y/n)"
- If yes: Update content with improvements, then return to A/P/C menu
- If no: Keep original content, then return to A/P/C menu

#### If 'C' (Continue):

- Append the final content to `{default_output_file}`
- Update frontmatter: `stepsCompleted: [1, 2]`
- Load `./step-03-users.md`

## APPEND TO DOCUMENT:

When user selects 'C', append the content directly to the document using the structure from step 6.

## SUCCESS METRICS:

✅ Clear problem statement that resonates with target users
✅ Compelling solution vision that addresses the core problem
✅ Unique differentiators that provide competitive advantage
✅ Executive summary that captures the product essence
✅ A/P/C menu presented and handled correctly
✅ Content properly appended to document when C selected

## FAILURE MODES:

❌ Accepting vague problem statements without pushing for specificity
❌ Creating solution vision without fully understanding the problem
❌ Missing unique differentiators or competitive insights
❌ Generating vision without real user input and collaboration
❌ Not presenting A/P/C menu after content generation
❌ Appending content without user selecting 'C'

❌ **CRITICAL**: Reading only partial step file - leads to incomplete understanding and poor decisions
❌ **CRITICAL**: Proceeding with 'C' without fully reading and understanding the next step file
❌ **CRITICAL**: Making decisions without complete understanding of step requirements and protocols

## CONTEXT INPUTS:

If research or brainstorming documents were loaded in step 1, incorporate insights from those documents into the vision discovery:

- "I see from the research that..."
- "In the brainstorming session, you mentioned..."
- "The project documentation suggests..."

## NEXT STEP:

After user selects 'C' and content is saved to document, load `./step-03-users.md` to define target users.

Remember: Do NOT proceed to step-03 until user explicitly selects 'C' from the A/P/C menu and content is saved!
