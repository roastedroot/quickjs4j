package io.roastedroot.quickjs4j.example;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Artifact {
    @JsonProperty("name")
    private final String name;

    @JsonProperty("type")
    private final String type;

    @JsonProperty("content")
    private final String content;

    public Artifact(String name, String type, String content) {
        this.name = name;
        this.type = type;
        this.content = content;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public String content() {
        return content;
    }
}
