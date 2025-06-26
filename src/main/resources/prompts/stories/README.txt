STORY PROMPTS SYSTEM
=====================

This directory contains age-specific prompts and motivational templates for the story endpoint functionality.
These prompts are designed to motivate and guide children in creating interesting, engaging stories.

FILE STRUCTURE:

AGE-SPECIFIC PROMPTS:
- age-6-8.txt    -> Stories for early elementary kids (simple, magical, encouraging)
- age-9-10.txt   -> Stories for middle elementary kids (more complex plots, character development)
- age-11-12.txt  -> Stories for pre-teens (sophisticated themes, deeper character work)
- age-13-14.txt  -> Stories for early teens (complex themes, advanced techniques)
- age-15-16.txt  -> Stories for older teens (professional-level concepts, artistic voice)

MOTIVATIONAL TEMPLATES:
- start-templates.txt    -> Encouraging messages for story creation
- continue-templates.txt -> Motivational responses for story continuation
- templates-README.txt   -> Template system documentation

HOW IT WORKS:
1. The StoryService determines the user's age group from their profile
2. Loads the appropriate prompt file for that age group as the system message
3. Loads motivational templates from the template files
4. Randomly selects and applies templates to user messages for encouragement
5. Creates an interactive storytelling experience tailored to the child's developmental level

DESIGN PRINCIPLES:
- Age-appropriate complexity and themes
- Encouraging and supportive tone
- Focus on creativity and imagination
- Safe, positive content
- Progressive skill-building as children grow
- Configurable templates without code changes

TEMPLATE INTEGRATION:
The service now uses configurable templates loaded from files:
- Start templates: "What an exciting story title! Let's create something amazing together. %s"
- Continue templates: "Fantastic storytelling! %s What happens next?"

Templates are randomly selected to provide variety and keep interactions fresh.

This combination of age-specific prompts and encouraging templates creates a personalized
storytelling mentor experience for each child. 