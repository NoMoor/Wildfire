package wildfire.wildfire.states;

import java.awt.Color;
import java.awt.Point;

import wildfire.input.CarData;
import wildfire.input.DataPacket;
import wildfire.output.ControlsOutput;
import wildfire.vector.Vector2;
import wildfire.vector.Vector3;
import wildfire.wildfire.Wildfire;
import wildfire.wildfire.actions.DodgeAction;
import wildfire.wildfire.actions.HopAction;
import wildfire.wildfire.actions.RecoveryAction;
import wildfire.wildfire.actions.WavedashAction;
import wildfire.wildfire.obj.State;
import wildfire.wildfire.utils.Behaviour;
import wildfire.wildfire.utils.Constants;
import wildfire.wildfire.utils.Handling;
import wildfire.wildfire.utils.Physics;
import wildfire.wildfire.utils.Utils;

public class FallbackState extends State {
	
	/*
	 * These two mystical values hold the secrets to this state
	 */
	private static final double dropoff = 0.152, scope = 0.4;
	
	/*
	 * Yeah this one too, I guess
	 */
	private final int targetPly = 7;

	public FallbackState(Wildfire wildfire){
		super("Fallback", wildfire);
	}

	@Override
	public boolean ready(DataPacket input){
		return false;
	}

	@Override
	public ControlsOutput getOutput(DataPacket input){
		CarData car = input.car;
		boolean wall = Behaviour.isOnWall(car);
		double distance = wildfire.impactPoint.getPosition().distance(car.position);
		
		// Goal.
		Vector2 goal = Behaviour.getTarget(car, input.ball);
		wildfire.renderer.drawCrosshair(car, goal.withZ(Constants.BALLRADIUS), Color.WHITE, 125);
		
		// Drive down the wall.
		if(wall && wildfire.impactPoint.getTime() > 1){
			wildfire.renderer.drawString2d("Wall", Color.WHITE, new Point(0, 20), 2, 2);
			return Handling.driveDownWall(input);
		}

		// Impact point.
		Vector3 impactPoint = wildfire.impactPoint.getPosition();
		wildfire.renderer.renderPrediction(wildfire.ballPrediction, Color.yellow, 0, wildfire.impactPoint.getFrame());
		wildfire.renderer.drawCrosshair(car, impactPoint, Color.MAGENTA, 125);

		// Avoid own-goaling.
		Vector2 trace = Utils.traceToY(car.position.flatten(), impactPoint.minus(car.position).flatten(), Utils.teamSign(car) * -Constants.PITCHLENGTH);
//		boolean avoidOwnGoal = (trace != null && Math.abs(trace.x) < Constants.GOALHALFWIDTH + 900);
		boolean avoidOwnGoal = !Behaviour.correctSideOfTarget(car, input.ball.position) && trace != null;
		if(avoidOwnGoal){
			impactPoint = new Vector3(impactPoint.x - Math.signum(trace.x) * Utils.clamp(distance / 3.9, 85, 800), impactPoint.y, impactPoint.z);
			wildfire.renderer.drawCrosshair(car, impactPoint, Color.PINK, 125);
		}

		// Target.
		Vector3 localTarget = getLocalPosition(car, new Vector3(), Utils.toLocal(car, goal.withZ(Constants.BALLRADIUS)), 0, Utils.toLocal(car, impactPoint));
		Vector3 target = Utils.toGlobal(car, localTarget);
		target = Behaviour.goalStuck(car, target);
		wildfire.renderer.drawCircle(Color.ORANGE, target, Constants.BALLRADIUS * 0.2);
				
		/*
		 * Actions
		 */
		double velocityTowardsImpact = car.velocityDir(wildfire.impactPoint.getPosition().minus(car.position).flatten());
		double velocity = car.velocity.magnitude();
		double forwardVelocity = car.forwardVelocity;
		double steerImpact = Handling.aim(car, wildfire.impactPoint.getPosition().flatten());
		if(distance < (wall ? 300 : (car.isSupersonic ? 800 : 500)) && velocityTowardsImpact > (wall ? 900 : 1200) && forwardVelocity > 0.95){
			double dodgeAngle = steerImpact;
			double goalAngle = Vector2.angle(wildfire.impactPoint.getPosition().minus(car.position).flatten(), goal.minus(car.position.flatten()));

			//Check if we can go for a shot
			Vector2 traceGoal = Utils.traceToY(car.position.flatten(), wildfire.impactPoint.getPosition().minus(car.position).flatten(), Utils.teamSign(car) * Constants.PITCHLENGTH);
			boolean shotOpportunity = (traceGoal != null && Math.abs(traceGoal.x) < 1200);

			if((Math.abs(dodgeAngle) < 0.3 && !shotOpportunity) || goalAngle < 0.5 || Utils.teamSign(car) * input.ball.velocity.y < -500 ||  Utils.teamSign(car) * car.position.y < -3000 || wall){
				//If the dodge angle is small, make it big - trust me, it works
				if(Math.abs(dodgeAngle) < Math.toDegrees(80)){
					dodgeAngle = Utils.clamp(dodgeAngle * (wall ? 2 : 3.25), -Math.PI, Math.PI);
				}
				currentAction = new DodgeAction(this, dodgeAngle, input);
			}
		}else if(Behaviour.isCarAirborne(car)){
			currentAction = new RecoveryAction(this, input.elapsedSeconds);
		}else if(wall && Math.abs(car.position.x) < Constants.GOALHALFWIDTH - 50){
			currentAction = new HopAction(this, input, wildfire.impactPoint.getPosition().flatten());
		}else if(wildfire.impactPoint.getTime() > (avoidOwnGoal ? 1.45 : 2.05) && !car.isSupersonic 
				&& velocity > (car.boost == 0 ? 1200 : 1500) && Math.abs(steerImpact) < 0.2){
			// Front flip for speed.
			if(car.boost < 10 || Math.abs(steerImpact) > 0.1 || wildfire.impactPoint.getTime() < 2.5 || Utils.distanceToWall(car.position) < 300){
				currentAction = new DodgeAction(this, 0, input);
			}else{
				// Throw out a little wavedash.
				currentAction = new WavedashAction(this, input);
			}
		}
		if(currentAction != null && !currentAction.failed) return currentAction.getOutput(input);
		currentAction = null;
		
		/*
		 *  Controls and handling.
		 */
		double radians = Handling.aim(car, target);
		
		boolean movingFast = (Math.abs(forwardVelocity) > 1600), movingSlow = (forwardVelocity < 900 && forwardVelocity > -900);
		boolean insideTurningRadius = (Handling.insideTurningRadius(car, target) && Math.abs(radians) > 0.24);
		if(insideTurningRadius) wildfire.renderer.drawTurningRadius(Color.WHITE, car);

		double maxVelocity = (insideTurningRadius && movingFast ? Math.min(Physics.maxVelForTurn(car, target), Constants.SUPERSONIC) : Constants.SUPERSONIC);
		double acceleration = (maxVelocity - car.forwardVelocity) / 0.025;
		double throttle = Handling.produceAcceleration(car, acceleration);
//		double throttle = (insideTurningRadius && movingFast ? -1 : 10);

        return new ControlsOutput().withSteer(-radians * 3).withThrottle(throttle).withBoost(Math.abs(radians) < 0.2 && !car.isSupersonic && throttle > 1)
        		.withSlide(Handling.canHandbrake(input.car) && Math.abs(radians) > 1.2); // && movingSlow
	}
	
	private Vector3 getLocalPosition(CarData car, Vector3 startLocal, Vector3 goalLocal, int ply, Vector3 impactPointLocal){
		if(ply >= 30) return null;
		
		double distance = impactPointLocal.distanceFlat(startLocal);
		
		Vector3 endLocal = impactPointLocal.plus(impactPointLocal.minus(goalLocal).withZ(0).scaledToMagnitude(distance * scope)).withZ(0);
		endLocal = startLocal.plus(endLocal.minus(startLocal).scaled(dropoff));//.confine(35, 50);
		
		wildfire.renderer.drawLine3d(Color.RED, Utils.toGlobal(car, startLocal).toFramework(), Utils.toGlobal(car, endLocal).toFramework());
		Vector3 next = getLocalPosition(car, endLocal, goalLocal, ply + 1, impactPointLocal);
		return ply < targetPly ? (ply == targetPly ? startLocal : next) : endLocal;
	}

}
