package com.example.typeracer.entities;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    private String id;
    private String name;
    private Long userId;
    private Integer charIndex;
    private Integer correctChars;
    private Double wpm;
    private boolean ready;
    private boolean host;
}
