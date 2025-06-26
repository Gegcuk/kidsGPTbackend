MODERATION TEMPLATES SYSTEM
============================

This directory contains configurable templates and messages for content moderation and validation.

FILE STRUCTURE:

PROMPT TEMPLATES:
- age-aware-prompt-template.txt     -> Template for age-specific moderation prompts
- ai-validation-system-template.txt -> System prompt template for AI validation
- ai-validation-user-template.txt   -> User prompt template for AI validation

AGE-SPECIFIC GUIDELINES:
- age-guidelines/age-6-8.txt   -> Content guidelines for 6-8 year olds
- age-guidelines/age-9-10.txt  -> Content guidelines for 9-10 year olds
- age-guidelines/age-11-12.txt -> Content guidelines for 11-12 year olds
- age-guidelines/age-13-14.txt -> Content guidelines for 13-14 year olds
- age-guidelines/age-15-16.txt -> Content guidelines for 15-16 year olds

ERROR MESSAGES:
- error-messages.txt -> Configurable error messages for validation failures

TEMPLATE FORMAT:
- Templates use placeholders like %s and %d for dynamic content
- Age guidelines are simple text files with bullet points
- Error messages use KEY=VALUE format for easy localization

USAGE:
The ModerationUtil loads these templates at runtime and uses them to:
1. Create age-specific moderation prompts
2. Generate AI validation system and user prompts
3. Load age-appropriate content guidelines
4. Provide consistent error messages

CUSTOMIZATION:
- Modify templates to change validation behavior
- Update age guidelines to adjust content standards
- Customize error messages for different languages/tones
- All changes take effect without code recompilation

This system allows easy customization of moderation behavior without touching the core validation logic.