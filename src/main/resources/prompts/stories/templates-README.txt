STORY TEMPLATES SYSTEM
======================

This directory contains motivational templates used to encourage kids during storytelling.

FILE STRUCTURE:
- start-templates.txt     -> Templates for when starting a new story
- continue-templates.txt  -> Templates for when continuing an existing story

TEMPLATE FORMAT:
- Each line contains one template
- Use %s as placeholder for user's content
- Keep messages encouraging and age-appropriate
- Templates are selected randomly for variety

USAGE:
The StoryService loads these templates at runtime and randomly selects one to:
1. Decorate the user's initial story idea (start templates)
2. Respond to story continuations (continue templates)

EXAMPLES:
Start: "Great choice for a story! Let's start creating. %s"
Continue: "Amazing creativity! %s What exciting twist should we add?"

CUSTOMIZATION:
- Add new templates by adding new lines
- Modify existing templates to change tone/style
- Templates should always include %s placeholder
- Keep messages positive and encouraging

This system allows easy customization of the storytelling experience without code changes. 