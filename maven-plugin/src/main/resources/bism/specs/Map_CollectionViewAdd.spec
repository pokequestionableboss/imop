pointcut pc1 before MethodCall(* *.*Collection.add(..)) || before MethodCall(* *.*Collection.addAll(..))
pointcut pc2 after MethodCall(* *.*Map.keySet()) || after MethodCall(* *.*Map.entrySet()) || after MethodCall(* *.*Map.values())

event e1("Map_CollectionViewAdd", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("Map_CollectionViewAdd", name, [getMethodReceiver, getMethodResult]) on pc2 to Monitor.receiveEvents(String, String, List)
