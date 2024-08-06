pointcut pc1 before MethodCall(* *.*Iterator.remove())
pointcut pc2 before MethodCall(* *.*Iterator.next())

event e1("Iterator_RemoveOnce", name, [getMethodReceiver, getMethodResult]) on pc1 to Monitor.receiveEvents(String, String, List)
event e2("Iterator_RemoveOnce", name, [getMethodReceiver, getMethodResult]) on pc2 to Monitor.receiveEvents(String, String, List)
