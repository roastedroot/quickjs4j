package io.roastedroot.quickjs4j.jackson2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AnnotatedPoint {
    @JsonProperty("x")
    private final int x;

    @JsonProperty("y")
    private final int y;

    @JsonCreator
    public AnnotatedPoint(@JsonProperty("x") int x, @JsonProperty("y") int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AnnotatedPoint)) {
            return false;
        }
        AnnotatedPoint point = (AnnotatedPoint) o;
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
