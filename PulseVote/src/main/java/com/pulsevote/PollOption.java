package com.pulsevote;

import jakarta.persistence.*;

@Entity
public class PollOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String text;
    private int votes;

    protected PollOption() {}
    public PollOption(String text) { this.text = text; }

    public Long getId() { return id; }
    public String getText() { return text; }
    public int getVotes() { return votes; }
    public void addVote() { votes++; }
}
