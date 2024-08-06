pointcut pc1 before MethodCall(* *.*Map.putAll(Map))
pointcut pc2 before MethodCall(* *.*Map.put(Object, Object))
pointcut pc3 before MethodCall(* *.*TreeMap.new(Map))

event e1("TreeMap_Comparable", name, [getMethodReceiver, getMethodResult, getAllMethodArgs]) on pc1 to Monitor.receiveEvents(String, String, List)
