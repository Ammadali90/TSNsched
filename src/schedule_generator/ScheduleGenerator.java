package schedule_generator;

import java.io.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalTime;
import java.util.*;

import com.microsoft.z3.*;

/**
 * [Class]: ScheduleGenerator
 * [Usage]: Used to generate a schedule based on the properties of
 * a given network through the method generateSchedule. Will create
 * a log file and store the timing properties on the cycles and flows.
 */
public class ScheduleGenerator {
    
	   @SuppressWarnings("serial")
	   class TestFailedException extends Exception
	   {
	       public TestFailedException()
	       {
	           super("Check FAILED");
	       }
	   };
	   
	   /**
	    * [Method]: stringToFloat
	    * [Usage]: After evaluating the model, z3 allows the
	    * user to retrieve the values of variables. This 
	    * values are stored as strings, which are converted 
	    * by this function in order to be stored in the 
	    * classes variables. Often these z3 variables are 
	    * also on fraction form, which is also handled by 
	    * this function.
	    * 
	    * @param str   String containing value to convert to float
	    * @return      Float value of the given string str
	    */
	   public float stringToFloat(String str) {
	       BigDecimal val1;
	       BigDecimal val2;
	       float result = 0;
	       
	       if(str.contains("/")) {
	           val1 = new BigDecimal(str.split("/")[0]);
	           val2 = new BigDecimal(str.split("/")[1]);
	           result = val1.divide(val2, MathContext.DECIMAL128).floatValue();

	       } else {
	    	   try{
 	    		    result = Float.parseFloat(str);
	    	    }catch(NumberFormatException e){
	    	        result = -1;
	    	    }
	       }
	       
	       return result;
	   }
	   
	   
	   /**
	    * [Method]: createContext
	    * [Usage]: Returns a z3 context used as an environment 
	    * for creation of z3 variables and operations
	    * 
	    * @return  A z3 context
	    */
	   public Context createContext() {
	       System.out.println("findSchedulerModel\n");
           
           try
           {
                com.microsoft.z3.Global.ToggleWarningMessages(true);
                System.out.print("Z3 Major Version: ");
                System.out.println(Version.getMajor());
                System.out.print("Z3 Full Version: ");
                System.out.println(Version.getString());
                System.out.print("Z3 Full Version String: ");
                System.out.println(Version.getFullVersion());
                System.out.println("");

                
                { // These examples need model generation turned on.
                    HashMap<String, String> cfg = new HashMap<String, String>();
                    cfg.put("model", "true");
                    Context ctx = new Context(cfg);

                    return ctx;
                }
            } catch (Z3Exception ex)
            {
                System.out.println("Z3 Managed Exception: " + ex.getMessage());
                System.out.println("Stack trace: ");
                ex.printStackTrace(System.out);
            } catch (Exception ex)
            {
                System.out.println("Unknown Exception: " + ex.getMessage());
                System.out.println("Stack trace: ");
                ex.printStackTrace(System.out);
            } 
           
            return null;
	   }
	   
	   
	   /**
	    * [Method]: closeContext
	    * [Usage]: Clears and close the context used to 
	    * generate the schedule.
	    * 
	    * @param ctx   Context to be cleared
	    */
	   public void closeContext(Context ctx) {
	       try
           {
                
                { 
                    ctx.close();
                }
                
                //Log.close();
                if (Log.isOpen())
                    System.out.println("Log is still open!");
            } catch (Z3Exception ex)
            {
                System.out.println("Z3 Managed Exception: " + ex.getMessage());
                System.out.println("Stack trace: ");
                ex.printStackTrace(System.out);
            } catch (Exception ex)
            {
                System.out.println("Unknown Exception: " + ex.getMessage());
                System.out.println("Stack trace: ");
                ex.printStackTrace(System.out);
            } 
	   }
	   
	   /**
	    * [Method]: writePathTree
	    * [Usage]: This is a recursive function used to 
	    * navigate through the pathTree, storing information
	    * about the switches and flowFramengts in the nodes
	    * and printing data in the log.
	    * 
	    * @param pathNode  Current node of pathTree (should start with root)
	    * @param model     Output model generated by z3
	    * @param ctx       z3 context used to generate the model
	    * @param out       PrintWriter stream to output log file
	    */
	   public void writePathTree(PathNode pathNode, Model model, Context ctx, PrintWriter out) {
	       Switch swt;
	       IntExpr indexZ3 = null;
	    
	       if((pathNode.getNode() instanceof Device) && (pathNode.getParent() != null)) {
	           out.println("    [END OF BRANCH]");
                
	       }
	       
	       
	       /*
	        * Once given a node, an iteration through its children will begin. For
	        * each switch children, there will be a flow fragment, and to each device
	        * children, there will be an end of branch.
	        * 
	        * The logic for storing and printing the data on the publish subscribe
	        * flows is similar but easier than the unicast flows. The pathNode object
	        * stores references to both flow fragment and switch, so no search is needed.
	        */
           for(PathNode child : pathNode.getChildren()) {
               if(child.getNode() instanceof Switch) {
                   
                   for(FlowFragment ffrag : child.getFlowFragments()) {
                       out.println("    Fragment name: " + ffrag.getName());
                       out.println("        Fragment node: " + ffrag.getNodeName());
                       out.println("        Fragment next hop: " + ffrag.getNextHop());
                       out.println("        Fragment priority: " + model.eval(ffrag.getFragmentPriorityZ3(), false));
                       for(int index = 0; index < ((TSNSwitch) child.getNode()).getPortOf(ffrag.getNextHop()).getCycle().getNumOfSlots(); index++) {
                    	   indexZ3 = ctx.mkInt(index);
                    	   out.println("        Fragment slot start " + index + ": " 
                                   + this.stringToFloat(
                                           model.eval(((TSNSwitch) child.getNode())
                                                  .getPortOf(ffrag.getNextHop())
                                                  .getCycle()
                                                  .slotStartZ3(ctx, ffrag.getFragmentPriorityZ3(), indexZ3) 
                                                  , false).toString()
                                       ));
                    	   out.println("        Fragment slot duration " + index + " : " 
                                    + this.stringToFloat(
                                        model.eval(((TSNSwitch) child.getNode())
                                                   .getPortOf(ffrag.getNextHop())
                                                   .getCycle()
                                                   .slotDurationZ3(ctx, ffrag.getFragmentPriorityZ3(), indexZ3) 
                                                   , false).toString()));
                
                       }
                       
                       out.println("        Fragment times-");
                       ffrag.getParent().addToTotalNumOfPackets(ffrag.getNumOfPacketsSent());
                       
                       for(int i = 0; i < ffrag.getParent().getNumOfPacketsSent(); i++) {
                    	   // System.out.println(((TSNSwitch)child.getNode()).departureTime(ctx, i, ffrag));
                    	   // System.out.println(((TSNSwitch)child.getNode()).arrivalTime(ctx, i, ffrag));
                    	   // System.out.println(((TSNSwitch)child.getNode()).scheduledTime(ctx, i, ffrag));
                           
                    	   if(i < ffrag.getNumOfPacketsSent()) {
		                	   out.println("          (" + Integer.toString(i) + ") Fragment departure time: " + this.stringToFloat(model.eval(((TSNSwitch) child.getNode()).departureTime(ctx, i, ffrag) , false).toString()));
		                	   out.println("          (" + Integer.toString(i) + ") Fragment arrival time: " + this.stringToFloat(model.eval(((TSNSwitch) child.getNode()).arrivalTime(ctx, i, ffrag) , false).toString()));
		                       out.println("          (" + Integer.toString(i) + ") Fragment scheduled time: " + this.stringToFloat(model.eval(((TSNSwitch) child.getNode()).scheduledTime(ctx, i, ffrag) , false).toString()));
		                       out.println("          ----------------------------");
                    	   }
                           
                    	   ffrag.setFragmentPriority(
                			   Integer.parseInt(
                    			   model.eval(ffrag.getFragmentPriorityZ3(), false).toString()
        					   )
            			   );
                    	   
                           ffrag.addDepartureTime(
                               this.stringToFloat(
                                   model.eval(((TSNSwitch) child.getNode()).departureTime(ctx, i, ffrag) , false).toString()   
                               )
                           );
                           ffrag.addArrivalTime(
                               this.stringToFloat(
                                   model.eval(((TSNSwitch) child.getNode()).arrivalTime(ctx, i, ffrag) , false).toString()   
                               )
                           );
                           ffrag.addScheduledTime(
                               this.stringToFloat(
                                   model.eval(((TSNSwitch) child.getNode()).scheduledTime(ctx, i, ffrag) , false).toString()   
                               )
                           );
                           
                       }
                       
                       swt = (TSNSwitch) child.getNode();

                       for (Port port : ((TSNSwitch) swt).getPorts()) {

                           if(!port.getFlowFragments().contains(ffrag)) {
                               continue;
                           }

                           ArrayList<Float> listOfStart = new ArrayList<Float>();
                    	   ArrayList<Float> listOfDuration = new ArrayList<Float>();
                    	
                           
                           for(int index = 0; index < ((TSNSwitch) child.getNode()).getPortOf(ffrag.getNextHop()).getCycle().getNumOfSlots(); index++) {
                        	   indexZ3 = ctx.mkInt(index);
                        	   
                    		   listOfStart.add(
                				   this.stringToFloat(model.eval( 
                                       ((TSNSwitch) child.getNode())
                                       .getPortOf(ffrag.getNextHop())
                                       .getCycle().slotStartZ3(ctx, ffrag.getFragmentPriorityZ3(), indexZ3) , false).toString())
            				   );
                    		   listOfDuration.add(
                				   this.stringToFloat(model.eval( 
                                       ((TSNSwitch) child.getNode())
                                       .getPortOf(ffrag.getNextHop())
                                       .getCycle().slotDurationZ3(ctx, ffrag.getFragmentPriorityZ3(), indexZ3) , false).toString())
            				   );
                           }
                    	   
                    	   port.getCycle().addSlotUsed(
                               (int) this.stringToFloat(model.eval(ffrag.getFragmentPriorityZ3(), false).toString()), 
                               listOfStart, 
                               listOfDuration
                           );
                       }
                       
                   }
                   
                   this.writePathTree(child, model, ctx, out);
               } 
           }
           
	   }
	   
	   
	   public void configureNetwork(Network net, Context ctx, Solver solver) {
		   for(Flow flw : net.getFlows()) {
	    	   flw.convertUnicastFlow();
	    	   flw.setUpPeriods(flw.getPathTree().getRoot());
	       }
	       
	       for(Switch swt : net.getSwitches()) {
	    	   TSNSwitch auxSwt = (TSNSwitch) swt;
	    	   auxSwt.setUpCycleSize(solver, ctx);
	       }
	       
	       
	       // On all network flows: Data given by the user will be converted to z3 values 
	       for(Flow flw : net.getFlows()) {
	           flw.toZ3(ctx);
	       }
	       
	       // On all network switches: Data given by the user will be converted to z3 values
           for(Switch swt : net.getSwitches()) {
               ((TSNSwitch) swt).toZ3(ctx, solver);
           }
           
           // Sets up the hard constraint for each individual flow in the network
           net.setJitterUpperBoundRangeZ3(ctx, 25);
	       net.secureHC(solver, ctx);
	   }
	   
	   
	   /**
	    * [Method]: generateSchedule
	    * [Usage]: After creating a network, setting up the 
	    * flows and switches, the user now can call this 
	    * function in order calculate the schedule values
	    * using z3 
	    * 
	    * @param net   Network used as base to generate the schedule
	    */
	   public void generateSchedule(Network net) 
	   {
		   Context ctx = this.createContext(); //Creating the z3 context
	       Solver solver = ctx.mkSolver();     //Creating the solver to generate unknown values based on the given context
	       
	       
	        this.configureNetwork(net, ctx, solver);
	       // net.loadNetwork(ctx, solver);
	       

	       // A switch is picked in order to evaluate the unknown values
           TSNSwitch switch1 = null;
           switch1 = (TSNSwitch) net.getSwitches().get(0);
           // The duration of the cycle is given as a question to z3, so all the 
           // constraints will have to be evaluated in order to z3 to know this cycle
           // duration
           RealExpr switch1CycDuration = switch1.getCycle(0).getCycleDurationZ3();
           
           
	       /* find model for the constraints above */
	       Model model = null;
	       LocalTime time = LocalTime.now();
	       
	       
	       System.out.println("Rules set. Checking solver.");
	       System.out.println("Current time of the day: " + time);
	       
	       
	       if (Status.SATISFIABLE == solver.check())
	       {
	           model = solver.getModel();
	           System.out.println(model);
	           Expr v = model.evaluate(switch1CycDuration, false);
	           if (v != null)
	           {
	               System.out.println("Model generated.");
	                              
	               try {
	                   PrintWriter out = new PrintWriter("log.txt");
	                   
	                   out.println("SCHEDULER LOG:\n\n");
                       	                   
	                   out.println("SWITCH LIST:");
	                   
	                   // For every switch in the network, store its information in the log
	                   for(Switch auxSwt : net.getSwitches()) {
	                       out.println("  Switch name: " + auxSwt.getName());
	                       out.println("    Max packet size: " + auxSwt.getMaxPacketSize());
                           out.println("    Port speed: " + auxSwt.getPortSpeed());
                           out.println("    Time to Travel: " + auxSwt.getTimeToTravel());
	                       out.println("    Transmission time: " + auxSwt.getTransmissionTime());
//	                       out.println("    Cycle information -");
//	                       out.println("        First cycle start: " + model.eval(((TSNSwitch)auxSwt).getCycleStart(), false));
//	                       out.println("        Cycle duration: " + model.eval(((TSNSwitch)auxSwt).getCycleDuration(), false));
                           out.println("");
	                       /*
	                       for (Port port : ((TSNSwitch)auxSwt).getPorts()) {
                               out.println("        Port name (Virtual Index): " + port.getName());
                               out.println("        First cycle start: " + model.eval(port.getCycle().getFirstCycleStartZ3(), false));
                               out.println("        Cycle duration: " + model.eval(port.getCycle().getCycleDurationZ3(), false));
                               out.println(""); 
                           }
                           */
	                       
	                       
	                       // [EXTRACTING OUTPUT]: Obtaining the z3 output of the switch properties,
                           // converting it from string to float and storing in the objects
                                                    
	                       for (Port port : ((TSNSwitch)auxSwt).getPorts()) {
	                           port
                                   .getCycle()
                                   .setCycleStart(
                                       this.stringToFloat("" + model.eval(port.getCycle().getFirstCycleStartZ3(), false))
                                   );
	                       }
	                       
	                       // cycleDuration
	                       for (Port port : ((TSNSwitch)auxSwt).getPorts()) {
                               port
                                   .getCycle()
                                   .setCycleDuration(
                                       this.stringToFloat("" + model.eval(port.getCycle().getCycleDurationZ3(), false))
                                   );
	                       }
	                       
	                   }
	                   
	                   out.println("");

	                   out.println("FLOW LIST:");
	                   //For every flow in the network, store its information in the log
	                   for(Flow f : net.getFlows()) {
	                       out.println("  Flow name: " + f.getName());
	                       //out.println("    Flow priority:" + model.eval(f.getFlowPriority(), false));
	                       out.println("    Start dev. first t1: " + model.eval(f.getStartDevice().getFirstT1TimeZ3(), false));
	                       out.println("    Start dev. HC: " + model.eval(f.getStartDevice().getHardConstraintTimeZ3(), false));
	                       out.println("    Start dev. packet periodicity: " + model.eval(f.getStartDevice().getPacketPeriodicityZ3(), false));
	                       
	                       
	                       // IF FLOW IS UNICAST
	                       /*
	                       Observation: The flow is broken in smaller flow fragments.
                           In order to know the departure, arrival, scheduled times
                           and other properties of the flow the switch that the flow fragment
                           belongs to must be retrieved. The flow is then used on the switch 
                           to find the port to its destination. The port and the flow fragment
                           can now be used to retrieve information about the flow.
                           
                           The way in which unicast and publish subscribe flows are 
                           structured here are different. So this process is done differently
                           in each case.
	                       */
	                       if(f.getType() == Flow.UNICAST) {
	                           // TODO: Throw error. UNICAST data structure are not allowed at this point
	                    	   // Everything should had been converted into the multicast model.
	                       } else if(f.getType() == Flow.PUBLISH_SUBSCRIBE) { //IF FLOW IS PUB-SUB
	                           
	                           /*
	                            * In case of a publish subscribe flow, it is easier to 
	                            * traverse through the path three than iterate over the 
	                            * nodes as it could be done with the unicast flow.
	                            */
	                           
	                           PathTree pathTree;
	                           PathNode pathNode;
	                           
	                           pathTree = f.getPathTree();
	                           pathNode = pathTree.getRoot();
	                           
	                           out.println("    Flow type: Multicast");
	                           ArrayList<PathNode> auxNodes;
	                           ArrayList<FlowFragment> auxFlowFragments;
	                           int auxCount = 0;
	                           
	                           out.print("    List of leaves: ");
	                           for(PathNode node : f.getPathTree().getLeaves()) {
	                               out.print(((Device) node.getNode()).getName() + ", ");                                           
	                           }
	                           out.println("");
                               for(PathNode node : f.getPathTree().getLeaves()) {
                                   auxNodes = f.getNodesFromRootToNode((Device) node.getNode());
                                   auxFlowFragments = f.getFlowFromRootToNode((Device) node.getNode());
                                   
                                   out.print("    Path to " + ((Device) node.getNode()).getName() + ": ");
                                   auxCount = 0;
                                   for(PathNode auxNode : auxNodes) {
                                       if(auxNode.getNode() instanceof Device) {
                                           out.print(((Device) auxNode.getNode()).getName() + ", ");                                           
                                       } else if (auxNode.getNode() instanceof TSNSwitch) {
                                           out.print(
                                               ((TSNSwitch) auxNode.getNode()).getName() + 
                                               "(" + 
                                               auxFlowFragments.get(auxCount).getName() +
                                               "), ");
                                           auxCount++;
                                       }
                                       
                                   }
                                   out.println("");
                               }
                               out.println("");
                               
	                           //Start the data storing and log printing process from the root
	                           this.writePathTree(pathNode, model, ctx, out);                                
	                       }
	                       
	                       out.println("");
	                       
	                   }

	                   out.close();
	                   
                   } catch (FileNotFoundException e) {
                       e.printStackTrace();
                   }
                   
	           } else
	           {
	               System.out.println("Failed to evaluate");
	           }
	       } else
	       {
	           System.out.println("The specified constraints might not be satisfiable.");
	       }
	       
	       this.closeContext(ctx);
	       new XMLExporter(net);
	       // this.serializeNetwork(net, "network.ser");
	   }


	   /**
	    * [Method]: serializateNetwork
	    * [Usage]: Serialize the primitive objects of the network object.
	    * The serialized file is stored in the path string folder. Can be used
	    * to store the data of a network and the values of the generated 
	    * schedule.
	    * 
	    * @param net		Network object to be serialized
	    * @param path		Path of for the serialized object file
	    */
	   public void serializeNetwork(Network net, String path) {
		   
	    	try {
	            FileOutputStream fileOut = new FileOutputStream(path);
	            ObjectOutputStream out = new ObjectOutputStream(fileOut);
	            out.writeObject(net);
	            out.close();
	            fileOut.close();
	    		System.out.printf("Serialized data is saved in network.ser");
	         } catch (Exception i) {
	            i.printStackTrace();
	         }
	    }
	   
	   /**
	    * [Method]: deserializeNetwork
	    * [Usage]: From a serialized object file, load the primitive
	    * values of the stored object.
	    * 
	    * @param path		Path of the serialized object file
	    * @return			The network object with all its primitive values
	    */
	   public Network deserializeNetwork(String path) {
		   Network net = null;
		   
		   try {
	           FileInputStream fileIn = new FileInputStream(path);
	           ObjectInputStream in = new ObjectInputStream(fileIn);
	           net = (Network) in.readObject();
	           in.close();
	           fileIn.close();			   
		   } catch (Exception i) {
 	           i.printStackTrace();
	           return null;
	       } 		   
		   return net;
	   }



}
