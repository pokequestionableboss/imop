pointcut pc1 before MethodCall(* *.*Map.putAll(Map))
pointcut pc2 before MethodCall(* *.*Map.put(..))

event e1("CharSequence_NotInMap", name, [getMethodReceiver, getMethodResult, getAllMethodArgs]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("CharSequence_NotInMap", name, [getMethodReceiver, getMethodResult, getAllMethodArgs]) on pc2 to Monitor.receiveEvents(String, String, List)
