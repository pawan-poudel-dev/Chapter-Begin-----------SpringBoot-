package com.pulsevote;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Poll {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String question;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "poll_id")
    @OrderBy("id")
    private List<PollOption> options = new ArrayList<>();

    protected Poll() {}
    public Poll(String question) { this.question = question; }

    public Long getId() { return id; }
    public String getQuestion() { return question; }
    public List<PollOption> getOptions() { return options; }
}
