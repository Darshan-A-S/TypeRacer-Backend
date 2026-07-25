package com.example.typeracer.entities;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class Player {

    private String id;
    private String name;
    private String webSessionId;
    private Integer charIndex;
    private Integer correctChars;
    private Double wpm;
    private boolean ready;
    private boolean host;
}