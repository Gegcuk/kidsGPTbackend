package uk.gegc.kidsgptbackend.features.story.infra.mapping;

import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.features.story.api.dto.StoryDto;
import uk.gegc.kidsgptbackend.features.story.api.dto.StoryListDto;
import uk.gegc.kidsgptbackend.features.story.api.dto.StoryMessageDto;
import uk.gegc.kidsgptbackend.features.story.domain.model.Story;
import uk.gegc.kidsgptbackend.features.story.domain.model.StoryMessage;

import java.util.List;

@Component
public class StoryMapper {

    public StoryDto toDto(Story story) {
        return new StoryDto(
                story.getId(),
                story.getTitle(),
                story.getStatus(),
                story.getMessages().stream()
                        .map(this::toMessageDto)
                        .toList(),
                story.getCreatedAt(),
                story.getUpdatedAt()
        );
    }

    public StoryListDto toListDto(Story story) {
        return new StoryListDto(
                story.getId(),
                story.getTitle(),
                story.getStatus(),
                story.getMessages().size(),
                story.getCreatedAt(),
                story.getUpdatedAt()
        );
    }

    public StoryMessageDto toMessageDto(StoryMessage message) {
        return new StoryMessageDto(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    public List<StoryDto> toDtoList(List<Story> stories) {
        return stories.stream()
                .map(this::toDto)
                .toList();
    }

    public List<StoryListDto> toListDtoList(List<Story> stories) {
        return stories.stream()
                .map(this::toListDto)
                .toList();
    }
}

