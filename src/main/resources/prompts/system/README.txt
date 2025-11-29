# Age-Specific System Prompts

This directory contains age-specific system prompts that are automatically selected based on the user's age. 
Each prompt is tailored to the developmental stage, language abilities, and educational needs of different age groups.

## Age Group Mapping

The system automatically selects the appropriate prompt based on user age:

- **age-6-8.txt**: Ages 6-8 (Early Elementary)
  - Simple vocabulary and short sentences
  - Basic concepts and play-based learning
  - Heavy use of emojis and encouragement
  - Focus on fundamental skills and good habits

- **age-9-10.txt**: Ages 9-10 (Late Elementary) 
  - More complex language while remaining accessible
  - Step-by-step explanations for complex topics
  - Introduction to problem-solving and critical thinking
  - Expanded subject areas and real-world connections

- **age-11-12.txt**: Ages 11-12 (Middle School/Preteen)
  - Rich vocabulary and sophisticated concepts
  - Advanced curiosity and hypothesis formation
  - Emotional intelligence and ethical discussions
  - STEM concepts and creative problem-solving

- **age-13-14.txt**: Ages 13-14 (Early Teen)
  - Mature language and abstract thinking
  - Independent thought and personal opinions
  - Complex academic subjects and social awareness
  - Identity exploration and ethical reasoning

- **age-15-16.txt**: Ages 15-16 (Mid-Late Teen)
  - Advanced vocabulary and academic rigor
  - Intellectual independence and original research
  - College preparation and career guidance
  - Leadership skills and global citizenship

## Implementation

The `AiChatServiceImpl.getSystemPromptResource()` method automatically selects the appropriate prompt file based on the user's age:

```java
private Resource getSystemPromptResource(Integer age) {
    if (age == null) {
        return systemPromptAge9_10; // Default fallback
    }
    
    if (age <= 8) {
        return systemPromptAge6_8;
    } else if (age <= 10) {
        return systemPromptAge9_10;
    } else if (age <= 12) {
        return systemPromptAge11_12;
    } else if (age <= 14) {
        return systemPromptAge13_14;
    } else {
        return systemPromptAge15_16;
    }
}
```

## Key Features

Each prompt includes:
- **Communication Style**: Age-appropriate language complexity and tone
- **Educational Goals**: Developmental objectives for that age group  
- **Subject Areas**: Topics and complexity levels suitable for the age
- **Teaching Methods**: How to present information effectively
- **Safety Guidelines**: Age-appropriate content boundaries
- **Engagement Techniques**: Methods to keep children interested and learning

## Benefits

- **Personalized Learning**: Content automatically adapts to developmental stage
- **Appropriate Challenge**: Neither too simple nor too complex for the age
- **Developmental Support**: Encourages growth in age-appropriate ways
- **Safety**: Maintains proper boundaries for each age group
- **Engagement**: Uses techniques that resonate with specific age groups

This system ensures that every child receives an educational experience perfectly tailored to their developmental needs and capabilities. 