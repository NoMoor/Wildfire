package wildfire.wildfire.states;

import java.awt.Color;
import java.awt.Point;
import java.util.Random;

import rlbot.flat.QuickChatSelection;
import wildfire.boost.BoostManager;
import wildfire.input.CarData;
import wildfire.input.DataPacket;
import wildfire.output.ControlsOutput;
import wildfire.vector.Vector2;
import wildfire.vector.Vector3;
import wildfire.wildfire.Wildfire;
import wildfire.wildfire.actions.DodgeAction;
import wildfire.wildfire.actions.WavedashAction;
import wildfire.wildfire.obj.BezierCurve;
import wildfire.wildfire.obj.KickoffSpawn;
import wildfire.wildfire.obj.State;
import wildfire.wildfire.utils.Behaviour;
import wildfire.wildfire.utils.Constants;
import wildfire.wildfire.utils.Handling;
import wildfire.wildfire.utils.Utils;

public class KickoffState extends State {
	
	/**
	 * Used for enabling/disabling the chance of fake kickoffs
	 */
	private final boolean fakeKickoffs = false;
	
	private KickoffSpawn spawn;
	private Random random;
		
	private float timeStarted;
	private boolean timedOut;
	private boolean fake;

	public KickoffState(Wildfire wildfire){
		super("Kickoff", wildfire);
		random = new Random();
	}

	@Override
	public boolean ready(DataPacket input){
		if(!Behaviour.isKickoff(input)) return false;
		
		wildfire.unlimitedBoost = (input.car.boost > 99);
		getSpawn(input.car.position);
		timeStarted = input.elapsedSeconds;

		//Choosing to fake
		timedOut = false;
		if(Behaviour.isTeammateCloser(input)){
			fake = true;
			wildfire.sendQuickChat(true, QuickChatSelection.Custom_Useful_Faking, QuickChatSelection.Information_GoForIt);
		}else if(fakeKickoffs){
			fake = ((random.nextFloat() < 0.2F || isUnfairKickoff(input)) && !Behaviour.hasTeammate(input) && spawn != KickoffSpawn.CORNER && Behaviour.hasOpponent(input));
//			fake = spawn != KickoffSpawn.CORNER;
		}else{
			fake = false;
		}
		
		return true;
	}
	
	private void getSpawn(Vector3 position){
		int x = (int)Math.abs(position.x);
		if(x > 1100){
			spawn = KickoffSpawn.CORNER;
		}else if(x > 200){
			spawn = KickoffSpawn.CORNERBACK;
		}else{
			spawn = KickoffSpawn.FULLBACK;
		}
	}

	@Override
	public boolean expire(DataPacket input){
		if(!fake) return !Behaviour.isKickoff(input);
		return input.ball.position.magnitude() > 650; //Distance from origin
	}

	@Override
	public ControlsOutput getOutput(DataPacket input){
		wildfire.renderer.drawString2d(spawn.toString(), Color.WHITE, new Point(0, 20), 2, 2);
		if(fake) wildfire.renderer.drawString2d("Fake", Color.WHITE, new Point(0, 40), 2, 2);
		
		//Time-out the fake kickoff if the opponent has taken too long
		if(fake && input.elapsedSeconds - timeStarted > 12){
			fake = false;
			timedOut = true;
			wildfire.sendQuickChat(QuickChatSelection.Reactions_Okay, QuickChatSelection.Custom_Toxic_WasteCPU);
		}
		
		if(!fake){
			Vector2 target;
			boolean dodge, wavedash;
			
			if(!timedOut){
				if(spawn == KickoffSpawn.CORNERBACK && input.car.velocity.magnitude() < 1000){
					//Collect the boost
					target = new Vector2(0, input.car.position.y * 0.62);
				}else if(spawn == KickoffSpawn.CORNER && input.car.velocity.magnitude() < 1800){
					//Line-up like a pro!
					target = new Vector2(0, input.car.position.y * 0.15);
				}else{
					target = new Vector2(0, Constants.BALLRADIUS * -Utils.teamSign(input.car));
				}
				
				dodge = (input.car.position.magnitude() < (spawn == KickoffSpawn.CORNER ? 760 : 800));
				wavedash = (!dodge && input.car.velocity.magnitude() > (spawn == KickoffSpawn.CORNER ? 1200 : 1150) && !input.car.isSupersonic);
			}else{
				//Generic kickoff
				target = input.ball.position.flatten(); 
				dodge = (input.car.position.magnitude() < 1000 && input.car.velocity.magnitude() > 1000);
				wavedash = false;
			}
			
			//Render
			BezierCurve bezier = new BezierCurve(input.car.position.flatten(), 
					input.car.position.plus(input.car.orientation.noseVector.scaledToMagnitude(250)).flatten(), 
					target, 
					input.ball.position.flatten());
			bezier.render(wildfire.renderer, Color.WHITE);
			wildfire.renderer.drawCircle(Color.LIGHT_GRAY, target, 30);
			
			if(!hasAction() && (dodge || wavedash) && Behaviour.isKickoff(input) && input.car.velocity.magnitude() > 500){
				if(dodge){
					double dodgeAngle = Handling.aim(input.car, (spawn == KickoffSpawn.CORNER ? new Vector2(-Math.signum(input.car.velocity.x) * Constants.BALLRADIUS, 0) : target));
					dodgeAngle = Utils.clamp(dodgeAngle * 3.5, -Math.PI, Math.PI);
					currentAction = new DodgeAction(this, dodgeAngle, input, true);
				}else{
					currentAction = new WavedashAction(this, input);
				}
				
				if(currentAction != null){
//					if(currentAction instanceof DodgeAction) currentAction.failed = false;
					if(!currentAction.failed) return currentAction.getOutput(input); //Start overriding
				}
			}
			
			double steerCorrectionRadians = Handling.aim(input.car, target);
	        return new ControlsOutput().withSteer((float)-steerCorrectionRadians * 2F).withThrottle(1).withBoost(true);
		}else{
			//Fake
			if(!BoostManager.getBoosts().get(input.car.team == 0 ? 0 : 33).isActive() || input.car.boost > 99){
				
				boolean reverse = (Math.abs(input.car.position.y) < 5200);
				double steerCorrectionRadians = Handling.aim(input.car, reverse ? Constants.homeGoal(input.car.team) : wildfire.impactPoint.getPosition().flatten());
				if(reverse){
					steerCorrectionRadians = Utils.invertAim(steerCorrectionRadians);
					double forwardMagnitude = input.car.forwardMagnitude();
			        return new ControlsOutput().withSteer((float)steerCorrectionRadians * 3F).withThrottle(-1F).withBoost(false).withSlide(forwardMagnitude < -200 && forwardMagnitude > -600);
				}else{
					return new ControlsOutput().withSteer((float)-steerCorrectionRadians * 2F).withThrottle(1F).withBoost(false).withSlide(false);
				}
			}else{
				
				//Get the boost in front of us
				Vector2 boost = new Vector2(0, -Utils.teamSign(input.car) * 4290);
				wildfire.renderer.drawCircle(Color.WHITE, boost, 50);
				double steerCorrectionRadians = Handling.aim(input.car, boost);
				if(Math.cos(steerCorrectionRadians) > 0){
					return new ControlsOutput().withSteer((float)steerCorrectionRadians * -3F).withThrottle(1F).withBoost(false);
				}else{
					return new ControlsOutput().withSteer((float)Utils.invertAim(steerCorrectionRadians) * 1.5F).withThrottle(-1F).withBoost(false);
				}
			}
		}
	}
	
	private boolean isUnfairKickoff(DataPacket input){
		double distanceBlue = Double.MAX_VALUE, distanceOrange = Double.MAX_VALUE;
		for(byte i = 0; i < input.cars.length; i++){
			if(input.cars[i] == null) continue;
			CarData car = input.cars[i];
			if(input.car.team == 0){
				distanceBlue = Math.min(distanceBlue, car.position.magnitude());
			}else{
				distanceOrange = Math.min(distanceOrange, car.position.magnitude());
			}
		}
		return input.car.team == 0 ? (distanceBlue > distanceOrange + 100) : (distanceOrange > distanceBlue + 100);
	}

}
