# Step 3: Target Users Discovery

## MANDATORY EXECUTION RULES (READ FIRST):

- 🛑 NEVER generate content without user input

- 📖 CRITICAL: ALWAYS read the complete step file before taking any action - partial understanding leads to incomplete decisions
- 🔄 CRITICAL: When loading next step with 'C', ensure the entire file is read and understood before proceeding
- ✅ ALWAYS treat this as collaborative discovery between PM peers
- 📋 YOU ARE A FACILITATOR, not a content generator
- 💬 FOCUS on defining who this product serves and how they interact with it
- 🎯 COLLABORATIVE persona development and user journey mapping

## EXECUTION PROTOCOLS:

- 🎯 Show your analysis before taking any action
- ⚠️ Present A/P/C menu after generating user segments content
- 💾 ONLY save when user chooses C (Continue)
- 📖 Update frontmatter `stepsCompleted: [1, 2, 3]` before loading next step
- 🚫 FORBIDDEN to load next step until C is selected

## COLLABORATION MENUS (A/P/C):

This step will generate content and present choices:

- **A (Advanced Elicitation)**: Use discovery protocols to develop deeper user insights
- **P (Party Mode)**: Bring multiple perspectives to validate user segments and journeys
- **C (Continue)**: Save the content to the document and proceed to next step

## PROTOCOL INTEGRATION:

- When 'A' selected: Execute {project-root}/.bmad/core/tasks/advanced-elicitation.xml
- When 'P' selected: Execute {project-root}/.bmad/core/workflows/party-mode/workflow.md
- PROTOCOLS always return to this step's A/P/C menu
- User accepts/rejects protocol changes before proceeding

## CONTEXT BOUNDARIES:

- Current document and frontmatter from previous steps are available
- Product vision and problem already defined in document
- Input documents from step 1 may contain user research or insights
- Focus on creating vivid, actionable user personas

## YOUR TASK:

Define target users with rich personas and map their key interactions with the product.

## USER DISCOVERY SEQUENCE:

### 1. Begin User Discovery

Start with user segmentation exploration:
"Now that we understand what {{project_name}} does, let's define who it's for.

**User Discovery:**

- Who experiences the problem we're solving?
- Are there different types of users with different needs?
- Who gets the most value from this solution?
- Are there primary users and secondary users we should consider?

Let's start by identifying the main user groups."

### 2. Primary User Segment Development

For each primary user segment, create rich personas:

#### Persona Development Process:

**Name & Context:**

- Give them a realistic name and brief backstory
- Define their role, environment, and context
- What motivates them? What are their goals?

**Problem Experience:**

- How do they currently experience the problem?
- What workarounds are they using?
- What are the emotional and practical impacts?

**Success Vision:**

- What would success look like for them?
- What would make them say "this is exactly what I needed"?

#### Primary User Questions:

- "Tell me about a typical person who would use {{project_name}}"
- "What's their day like? Where does our product fit in?"
- "What are they trying to accomplish that's hard right now?"

### 3. Secondary User Segment Exploration

Identify and develop secondary user segments:

#### Secondary User Considerations:

- "Who else benefits from this solution, even if they're not the primary user?"
- "Are there admin, support, or oversight roles we should consider?"
- "Who influences the decision to adopt or purchase this product?"
- "Are there partner or stakeholder users who matter?"

### 4. User Journey Mapping

Map key interactions for each user segment:

#### Journey Elements:

- **Discovery:** How do they find out about the solution?
- **Onboarding:** What's their first experience like?
- **Core Usage:** How do they use the product day-to-day?
- **Success Moment:** When do they realize the value?
- **Long-term:** How does it become part of their routine?

#### Journey Questions:

- "Walk me through how [Persona Name] would discover and start using {{project_name}}"
- "What's their 'aha!' moment?"
- "How does this product change how they work or live?"

### 5. Generate Target Users Content

Prepare the content to append to the document:

#### Content Structure:

When saving to document, append these Level 2 and Level 3 sections:

```markdown
## Target Users

### Primary Users

[Primary user segment content based on conversation]

### Secondary Users

[Secondary user segment content based on conversation, or N/A if not discussed]

### User Journey

[User journey content based on conversation, or N/A if not discussed]
```

### 6. Present Content and Menu

Show the generated user content and present choices:
"I've mapped out who {{project_name}} serves and how they'll interact with it. This helps us ensure we're building something that real people will love to use.

**Here's what I'll add to the document:**

[Show the complete markdown content from step 5]

**What would you like to do?**
[A] Advanced Elicitation - Let's dive deeper into these user personas and journeys
[P] Party Mode - Bring different perspectives to validate our user understanding
[C] Continue - Save this to the document and move to next step"

### 7. Handle Menu Selection

#### If 'A' (Advanced Elicitation):

- Execute {project-root}/.bmad/core/tasks/advanced-elicitation.xml with the current user content
- Process the enhanced user insights that come back
- Ask user: "Accept these improvements to the target users? (y/n)"
- If yes: Update content with improvements, then return to A/P/C menu
- If no: Keep original content, then return to A/P/C menu

#### If 'P' (Party Mode):

- Execute {project-root}/.bmad/core/workflows/party-mode/workflow.md with the current user personas
- Process the collaborative user validation and additional insights
- Ask user: "Accept these changes to the target users? (y/n)"
- If yes: Update content with improvements, then return to A/P/C menu
- If no: Keep original content, then return to A/P/C menu

#### If 'C' (Continue):

- Append the final content to `{default_output_file}`
- Update frontmatter: `stepsCompleted: [1, 2, 3]`
- Load `./step-04-metrics.md`

## APPEND TO DOCUMENT:

When user selects 'C', append the content directly to the document using the structure from step 5.

## SUCCESS METRICS:

✅ Rich, believable user personas with clear motivations
✅ Clear distinction between primary and secondary users
✅ User journeys that show key interaction points
✅ User segments that align with product vision and problem
✅ A/P/C menu presented and handled correctly
✅ Content properly appended to document when C selected

## FAILURE MODES:

❌ Creating generic user profiles without specific details
❌ Missing key user segments that are important to success
❌ User journeys that don't show how the product creates value
❌ Not connecting user needs back to the problem statement
❌ Not presenting A/P/C menu after content generation
❌ Appending content without user selecting 'C'

❌ **CRITICAL**: Reading only partial step file - leads to incomplete understanding and poor decisions
❌ **CRITICAL**: Proceeding with 'C' without fully reading and understanding the next step file
❌ **CRITICAL**: Making decisions without complete understanding of step requirements and protocols

## USER INSIGHTS SOURCES:

If user research documents were loaded in step 1, incorporate those insights:

- "From the user research we loaded, I see that..."
- "The interviews suggest users are struggling with..."
- "The survey data indicates preferences for..."

## OPTIONAL CONTENT:

User journey section is optional - only include if the journey mapping reveals important insights about how users will interact with the product and where value is created.

## NEXT STEP:

After user selects 'C' and content is saved to document, load `./step-04-metrics.md` to define success metrics.

Remember: Do NOT proceed to step-04 until user explicitly selects 'C' from the A/P/C menu and content is saved!
