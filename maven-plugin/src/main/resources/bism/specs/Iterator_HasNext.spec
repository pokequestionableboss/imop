pointcut pc1 before MethodCall(* *.*Iterator.next())
pointcut pc2 after MethodCall(* *.*Iterator.hasNext())

event e1("Iterator_HasNext", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("Iterator_HasNext", name, [getMethodReceiver, getMethodResult]) on pc2 to Monitor.receiveEvents(String, String, List)
