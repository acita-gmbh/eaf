# Step 6: Product Brief Completion

## MANDATORY EXECUTION RULES (READ FIRST):

- ✅ THIS IS A FINAL STEP - Product brief completion required

- 📖 CRITICAL: ALWAYS read the complete step file before taking any action - partial understanding leads to incomplete decisions
- 🔄 CRITICAL: When loading next step with 'C', ensure the entire file is read and understood before proceeding
- 🛑 NO content generation - this is a wrap-up step
- 📋 FINALIZE document and update workflow status
- 💬 FOCUS on completion, next steps, and suggestions
- 🎯 UPDATE workflow status files with completion information

## EXECUTION PROTOCOLS:

- 🎯 Show your analysis before taking any action
- 💾 Update the main workflow status file with completion information
- 📖 Suggest potential next workflow steps for the user
- 🚫 DO NOT load additional steps after this one

## TERMINATION STEP PROTOCOLS:

- This is a FINAL step - workflow completion required
- Output any remaining content if needed (none for this step)
- Update the main workflow status file with finalized document
- Suggest potential next steps for the user
- Mark workflow as complete in status tracking

## CONTEXT BOUNDARIES:

- Complete product brief document is available from all previous steps
- Workflow frontmatter shows all completed steps
- All collaborative content has been generated and saved
- Focus on completion, validation, and next steps

## YOUR TASK:

Complete the product brief workflow, update status files, and suggest next steps for the project.

## WORKFLOW COMPLETION SEQUENCE:

### 1. Announce Workflow Completion

Inform user that the product brief is complete:
"🎉 **Product Brief Complete, {{user_name}}!**

I've successfully collaborated with you to create a comprehensive Product Brief for {{project_name}}.

**What we've accomplished:**

- ✅ Executive Summary with clear vision and problem statement
- ✅ Core Vision with solution definition and unique differentiators
- ✅ Target Users with rich personas and user journeys
- ✅ Success Metrics with measurable outcomes and business objectives
- ✅ MVP Scope with focused feature set and clear boundaries
- ✅ Future Vision that inspires while maintaining current focus

**The complete Product Brief is now available at:** `{default_output_file}`

This brief serves as the foundation for all subsequent product development activities and strategic decisions."

### 2. Workflow Status Update

Update the main workflow status file:

- Check if `{output_folder}/bmm-workflow-status.yaml` exists
- If not, create it with basic structure
- Update workflow_status["product-brief"] = `{default_output_file}`
- Add completion timestamp and metadata
- Save file, preserving all comments and structure

### 3. Document Quality Check

Perform final validation of the product brief:

**Completeness Check:**

- Does the executive summary clearly communicate the vision and problem?
- Are target users well-defined with compelling personas?
- Do success metrics connect user value to business objectives?
- Is MVP scope focused and realistic?
- Does the brief provide clear direction for next steps?

**Consistency Check:**

- Do all sections align with the core problem statement?
- Is user value consistently emphasized throughout?
- Are success criteria traceable to user needs and business goals?
- Does MVP scope align with the problem and solution?

### 4. Suggest Next Steps

Provide guidance on logical next workflows:

**Recommended Next Workflow:**

1. `workflow prd` - Create detailed Product Requirements Document
   - Brief provides foundation for detailed requirements
   - User personas inform journey mapping
   - Success metrics become specific acceptance criteria
   - MVP scope becomes detailed feature specifications

**Other Potential Next Steps:** 2. `workflow create-ux-design` - UX research and design 3. `workflow create-architecture` - Technical architecture planning 4. `workflow domain-research` - Deep market or domain research (if needed)

**Strategic Considerations:**

- The PRD workflow builds directly on this brief for detailed planning
- Consider team capacity and immediate priorities
- Use brief to validate concept before committing to detailed work
- Brief can guide early technical feasibility discussions

### 5. Final Completion Confirmation

Confirm completion with user:
"**Your Product Brief for {{project_name}} is now complete and ready for the next phase!**

The brief captures everything needed to guide subsequent product development:

- Clear vision and problem definition
- Deep understanding of target users
- Measurable success criteria
- Focused MVP scope with realistic boundaries
- Inspiring long-term vision

**Ready to continue with:**

- PRD workflow for detailed requirements?
- UX design workflow for user experience planning?
- Architecture workflow for technical design?

**Or would you like to review the complete brief first?**

[Product Brief Complete]"

## SUCCESS METRICS:

✅ Product brief contains all essential sections
✅ All collaborative content properly saved to document
✅ Workflow status file updated with completion information
✅ Clear next step guidance provided to user
✅ Document quality validation completed
✅ User acknowledges completion and understands next options

## FAILURE MODES:

❌ Not updating workflow status file with completion information
❌ Missing clear next step guidance for user
❌ Not confirming document completeness with user
❌ Workflow not properly marked as complete in status tracking
❌ User unclear about what happens next

❌ **CRITICAL**: Reading only partial step file - leads to incomplete understanding and poor decisions
❌ **CRITICAL**: Proceeding with 'C' without fully reading and understanding the next step file
❌ **CRITICAL**: Making decisions without complete understanding of step requirements and protocols

## PRODUCT BRIEF COMPLETION CHECKLIST:

### Document Structure Complete:

- [ ] Executive Summary with vision and problem statement
- [ ] Core Vision with solution definition and differentiators
- [ ] Target Users with personas and user journeys
- [ ] Success Metrics with business objectives and KPIs
- [ ] MVP Scope with core features and boundaries
- [ ] Future Vision for long-term direction

### Process Complete:

- [ ] All steps completed with user confirmation
- [ ] All content saved to document
- [ ] Frontmatter properly updated
- [ ] Workflow status file updated
- [ ] Next steps clearly communicated

## NEXT STEPS GUIDANCE:

**Immediate Options:**

1. **PRD Workflow** - Transform brief into detailed requirements
2. **UX Design** - User experience research and design planning
3. **Architecture** - Technical system design and technology choices
4. **Review** - Validate brief with stakeholders before proceeding

**Recommended Sequence:**
Product Brief → PRD → (UX if needed) + Architecture → Development

## WORKFLOW FINALIZATION:

- Set `lastStep = 6` in document frontmatter
- Update workflow status file with completion timestamp
- Provide completion summary to user
- Do NOT load any additional steps

## FINAL REMINDER:

This product brief is now complete and serves as the strategic foundation for the entire product lifecycle. All subsequent design, architecture, and development work should trace back to the vision, user needs, and success criteria documented in this brief.

**Congratulations on completing the Product Brief for {{project_name}}!** 🎉

**Ready to transform this vision into detailed plans with the PRD workflow?**
