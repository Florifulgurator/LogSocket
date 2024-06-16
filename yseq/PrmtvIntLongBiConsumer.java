package florifulgurator.logsocket.yseq;

import java.util.Objects;

@FunctionalInterface
public interface PrmtvIntLongBiConsumer {

    void accept(int t, long u);

    default PrmtvIntLongBiConsumer andThen(PrmtvIntLongBiConsumer after) {
        Objects.requireNonNull(after);

        return (l, r) -> {
            accept(l, r);
            after.accept(l, r);
        };
    }
}
