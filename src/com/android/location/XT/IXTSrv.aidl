package com.android.location.XT;

import com.android.location.XT.IXTSrvCb;

interface IXTSrv
{
    boolean disable();
    boolean getStatus();
    void updateEulaStatus(boolean finalEulaStatus);
    String  getText(int which);
    void showDialog();
    void registerCallback(IXTSrvCb cb);
    void unregisterCallback(IXTSrvCb cb);
}
