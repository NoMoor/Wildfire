package wildfire.output;

import rlbot.ControllerState;

public class ControlsOutput implements ControllerState {

    // 0 is straight, -1 is hard left, 1 is hard right.
    private float steer;

    // -1 for front flip, 1 for back flip
    private float pitch;

    // 0 is straight, -1 is hard left, 1 is hard right.
    private float yaw;

    // 0 is straight, -1 is hard left, 1 is hard right.
    private float roll;

    // 0 is none, -1 is backwards, 1 is forwards
    private float throttle;

    private boolean jumpDepressed;
    private boolean boostDepressed;
    private boolean slideDepressed;

    public ControlsOutput(){
    }

    public ControlsOutput withSteer(double steer){
        this.steer = clamp(steer);
        return this;
    }

    public ControlsOutput withPitch(double pitch){
        this.pitch = clamp(pitch);
        return this;
    }

    public ControlsOutput withYaw(double yaw){
        this.yaw = clamp(yaw);
        return this;
    }

    public ControlsOutput withRoll(double roll){
        this.roll = clamp(roll);
        return this;
    }

    public ControlsOutput withThrottle(double throttle){
        this.throttle = clamp(throttle);
        return this;
    }

    public ControlsOutput withJump(boolean jumpDepressed){
        this.jumpDepressed = jumpDepressed;
        return this;
    }

    public ControlsOutput withBoost(boolean boostDepressed){
        this.boostDepressed = boostDepressed;
        return this;
    }

    public ControlsOutput withSlide(boolean slideDepressed){
        this.slideDepressed = slideDepressed;
        return this;
    }

    public ControlsOutput withJump(){
        this.jumpDepressed = true;
        return this;
    }

    public ControlsOutput withBoost(){
        this.boostDepressed = true;
        return this;
    }

    public ControlsOutput withSlide(){
        this.slideDepressed = true;
        return this;
    }

    private float clamp(float value){
        return Math.max(-1, Math.min(1, value));
    }
    
    private float clamp(double value){
        return clamp((float)value);
    }

    @Override
    public float getSteer(){
        return steer;
    }

    @Override
    public float getThrottle(){
        return throttle;
    }

    @Override
    public float getPitch(){
        return pitch;
    }

    @Override
    public float getYaw(){
        return yaw;
    }

    @Override
    public float getRoll(){
        return roll;
    }

    @Override
    public boolean holdJump(){
        return jumpDepressed;
    }

    @Override
    public boolean holdBoost(){
        return boostDepressed;
    }

    @Override
    public boolean holdHandbrake(){
        return slideDepressed;
    }
    
    public ControlsOutput withNone(){
    	this.withBoost(false).withSlide(false).withJump(false).withSteer(0).withPitch(0).withRoll(0).withYaw(0).withThrottle(0);
    	return this;
    }
    
}
