pointcut pc1 before MethodCall(* *.*Map.putAll(Map))
pointcut pc2 before MethodCall(* *.*Map.put(Object, Object))

event e1("Map_ItselfAsKey", name, [getMethodReceiver, getMethodResult, getAllMethodArgs]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("Map_ItselfAsKey", name, [getMethodReceiver, getMethodResult, getAllMethodArgs]) on pc2 to Monitor.receiveEvents(String, String, List)
