package com.resumescreening.model;

public class Candidate {

    private String name;
    private String email;
    private String phone;
    private double score;
    private int matchedKeywords;
    private int totalKeywords;
    private String filename;

    public Candidate() {}

    public Candidate(String name, String email, String phone,
                     double score, int matchedKeywords, int totalKeywords,
                     String filename) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.score = score;
        this.matchedKeywords = matchedKeywords;
        this.totalKeywords = totalKeywords;
        this.filename = filename;
    }

    // ── Getters & Setters ──────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public int getMatchedKeywords() { return matchedKeywords; }
    public void setMatchedKeywords(int matchedKeywords) { this.matchedKeywords = matchedKeywords; }

    public int getTotalKeywords() { return totalKeywords; }
    public void setTotalKeywords(int totalKeywords) { this.totalKeywords = totalKeywords; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
}
