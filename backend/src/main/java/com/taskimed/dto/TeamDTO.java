package com.taskimed.dto;

import java.util.List;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TeamDTO {
    private Long id;
    private String name;
    private String alias;

    private List<Long> userIds;
    private List<String> userNames;
    private List<Boolean> userActives;
}