# Prompts for Daily Tips

This directory contains prompt templates for generating daily educational tips or facts for different age groups.

- age-6-8.txt: Prompt for children aged 6-8
- age-9-10.txt: Prompt for children aged 9-10
- age-11-12.txt: Prompt for children aged 11-12
- age-13-14.txt: Prompt for teenagers aged 13-14
- age-15-16.txt: Prompt for teenagers aged 15-16

## Structure

- Each subdirectory under `prompts/` can represent a feature or endpoint (e.g., `tips/`, `chat/`, `missions/`).
- Each file within a subdirectory is a prompt template for a specific use case, age group, or context.

## Age Groups

The age groups correspond to the `AgeGroup` enum in the codebase:
- `AGE_6_8`: Ages 6-8
- `AGE_9_10`: Ages 9-10
- `AGE_11_12`: Ages 11-12
- `AGE_13_14`: Ages 13-14
- `AGE_15_16`: Ages 15-16

## Adding More Prompts

- To add a new prompt for a different feature, create a new subdirectory (e.g., `prompts/chat/`).
- To add a new age group or context, add a new file and update the `AgeGroup` enum accordingly. 