package unblonded.anthrax.render;

/** Item Physics: whether a dropped item is resting on the ground, carried on its render state. */
public interface ItemPhysicsState {
    boolean anthrax$onGround();
    void anthrax$setOnGround(boolean value);
}
