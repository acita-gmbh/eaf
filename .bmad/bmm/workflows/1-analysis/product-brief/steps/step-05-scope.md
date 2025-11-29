# Step 5: MVP Scope Definition

## MANDATORY EXECUTION RULES (READ FIRST):

- 🛑 NEVER generate content without user input

- 📖 CRITICAL: ALWAYS read the complete step file before taking any action - partial understanding leads to incomplete decisions
- 🔄 CRITICAL: When loading next step with 'C', ensure the entire file is read and understood before proceeding
- ✅ ALWAYS treat this as collaborative discovery between PM peers
- 📋 YOU ARE A FACILITATOR, not a content generator
- 💬 FOCUS on defining minimum viable scope and future vision
- 🎯 COLLABORATIVE scope negotiation that balances ambition with realism

## EXECUTION PROTOCOLS:

- 🎯 Show your analysis before taking any action
- ⚠️ Present A/P/C menu after generating scope content
- 💾 ONLY save when user chooses C (Continue)
- 📖 Update frontmatter `stepsCompleted: [1, 2, 3, 4, 5]` before loading next step
- 🚫 FORBIDDEN to load next step until C is selected

## COLLABORATION MENUS (A/P/C):

This step will generate content and present choices:

- **A (Advanced Elicitation)**: Use discovery protocols to optimize scope definition
- **P (Party Mode)**: Bring multiple perspectives to validate MVP scope
- **C (Continue)**: Save the content to the document and proceed to next step

## PROTOCOL INTEGRATION:

- When 'A' selected: Execute {project-root}/.bmad/core/tasks/advanced-elicitation.xml
- When 'P' selected: Execute {project-root}/.bmad/core/workflows/party-mode/workflow.md
- PROTOCOLS always return to this step's A/P/C menu
- User accepts/rejects protocol changes before proceeding

## CONTEXT BOUNDARIES:

- Current document and frontmatter from previous steps are available
- Product vision, users, and success metrics already defined
- Focus on defining what's essential for MVP vs. future enhancements
- Balance user needs with implementation feasibility

## YOUR TASK:

Define MVP scope with clear boundaries and outline future vision while managing scope creep.

## MVP SCOPE DISCOVERY SEQUENCE:

### 1. Begin Scope Definition

Start with MVP concept exploration:
"Now that we understand what {{project_name}} does, who it serves, and how we'll measure success, let's define what we need to build first.

**Scope Discovery:**

- What's the absolute minimum we need to deliver to solve the core problem?
- What features would make users say 'this solves my problem'?
- How do we balance ambition with getting something valuable to users quickly?

Let's start with the MVP mindset: what's the smallest version that creates real value?"

### 2. MVP Core Features Definition

Define essential features for minimum viable product:

#### MVP Feature Questions:

- "What's the core functionality that must work?"
- "Which features directly address the main problem we're solving?"
- "What would users consider 'incomplete' if it was missing?"
- "What features create the 'aha!' moment we discussed earlier?"

#### MVP Criteria:

- **Solves Core Problem:** Addresses the main pain point effectively
- **User Value:** Creates meaningful outcome for target users
- **Feasible:** Achievable with available resources and timeline
- **Testable:** Allows learning and iteration based on user feedback

### 3. Out of Scope Boundaries

Define what explicitly won't be in MVP:

#### Out of Scope Exploration:

- "What features would be nice to have but aren't essential?"
- "What functionality could wait for version 2.0?"
- "What are we intentionally saying 'no' to for now?"
- "How do we communicate these boundaries to stakeholders?"

#### Boundary Setting:

- Clear communication about what's not included
- Rationale for deferring certain features
- Timeline considerations for future additions
- Trade-off explanations for stakeholders

### 4. MVP Success Criteria

Define what makes the MVP successful:

#### Success Validation:

- "How will we know the MVP is successful?"
- "What metrics will indicate we should proceed beyond MVP?"
- "What user feedback signals validate our approach?"
- "What's the decision point for scaling beyond MVP?"

#### Success Gates:

- User adoption metrics
- Problem validation evidence
- Technical feasibility confirmation
- Business model validation

### 5. Future Vision Exploration

Define the longer-term product vision:

#### Vision Questions:

- "If this is wildly successful, what does it become in 2-3 years?"
- "What capabilities would we add with more resources?"
- "How does the MVP evolve into the full product vision?"
- "What markets or user segments could we expand to?"

#### Future Features:

- Post-MVP enhancements that build on core functionality
- Scale considerations and growth capabilities
- Platform or ecosystem expansion opportunities
- Advanced features that differentiate in the long term

### 6. Generate MVP Scope Content

Prepare the content to append to the document:

#### Content Structure:

When saving to document, append these Level 2 and Level 3 sections:

```markdown
## MVP Scope

### Core Features

[Core features content based on conversation]

### Out of Scope for MVP

[Out of scope content based on conversation, or N/A if not discussed]

### MVP Success Criteria

[MVP success criteria content based on conversation, or N/A if not discussed]

### Future Vision

[Future vision content based on conversation, or N/A if not discussed]
```

### 7. Present Content and Menu

Show the generated scope content and present choices:
"I've defined the MVP scope for {{project_name}} that balances delivering real value with realistic boundaries. This gives us a clear path forward while keeping our options open for future growth.

**Here's what I'll add to the document:**

[Show the complete markdown content from step 6]

**What would you like to do?**
[A] Advanced Elicitation - Let's optimize this scope definition
[P] Party Mode - Bring different perspectives to validate MVP scope
[C] Continue - Save this to the document and move to final step"

### 8. Handle Menu Selection

#### If 'A' (Advanced Elicitation):

- Execute {project-root}/.bmad/core/tasks/advanced-elicitation.xml with the current scope content
- Process the enhanced scope insights that come back
- Ask user: "Accept these improvements to the MVP scope? (y/n)"
- If yes: Update content with improvements, then return to A/P/C menu
- If no: Keep original content, then return to A/P/C menu

#### If 'P' (Party Mode):

- Execute {project-root}/.bmad/core/workflows/party-mode/workflow.md with the current MVP scope
- Process the collaborative scope validation and prioritization
- Ask user: "Accept these changes to the MVP scope? (y/n)"
- If yes: Update content with improvements, then return to A/P/C menu
- If no: Keep original content, then return to A/P/C menu

#### If 'C' (Continue):

- Append the final content to `{default_output_file}`
- Update frontmatter: `stepsCompleted: [1, 2, 3, 4, 5]`
- Load `./step-06-complete.md`

## APPEND TO DOCUMENT:

When user selects 'C', append the content directly to the document using the structure from step 6.

## SUCCESS METRICS:

✅ MVP features that solve the core problem effectively
✅ Clear out-of-scope boundaries that prevent scope creep
✅ Success criteria that validate MVP approach
✅ Future vision that inspires while maintaining focus
✅ A/P/C menu presented and handled correctly
✅ Content properly appended to document when C selected

## FAILURE MODES:

❌ MVP scope too large or includes non-essential features
❌ Missing clear boundaries leading to scope creep
❌ No success criteria to validate MVP approach
❌ Future vision disconnected from MVP foundation
❌ Not presenting A/P/C menu after content generation
❌ Appending content without user selecting 'C'

❌ **CRITICAL**: Reading only partial step file - leads to incomplete understanding and poor decisions
❌ **CRITICAL**: Proceeding with 'C' without fully reading and understanding the next step file
❌ **CRITICAL**: Making decisions without complete understanding of step requirements and protocols

## SCOPE NEGOTIATION PRINCIPLES:

**MVP Mindset:**

- Minimum features that create maximum user value
- Fast learning and iteration capability
- Clear success criteria for next phase decisions
- Stakeholder alignment on boundaries

**Scope Creep Prevention:**

- Explicit out-of-scope documentation
- Rationale for feature deferral
- Clear roadmap for future additions
- Regular scope validation against core problem

## OPTIONAL SECTIONS:

Future Vision and MVP Success Criteria sections are optional - include them if the discussion yields meaningful insights about long-term direction and success validation. Core Features and Out of Scope are essential sections.

## NEXT STEP:

After user selects 'C' and content is saved to document, load `./step-06-complete.md` to complete the product brief workflow.

Remember: Do NOT proceed to step-06 until user explicitly selects 'C' from the A/P/C menu and content is saved!
