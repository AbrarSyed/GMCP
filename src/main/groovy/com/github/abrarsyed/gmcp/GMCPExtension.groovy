package com.github.abrarsyed.gmcp

class GMCPExtension
{
	def minecraftVersion
	def forgeVersion
    private MCModInfo = null
	
	def mcmodinfo(Closure c)
	{
		// ????
	}
    
    public void setForgeVersion(Object obj)
    {
        if (obj instanceof Integer)
        {
            // TODO build number
        }
        else if (obj instanceof String)
        {
            def string = obj as String
            
            if (string.trim().equalsIgnoreCase("latest"))
            {
                // TODO: get latest forge version
            }
            if (string.trim().equalsIgnoreCase("rec") || string.trim().equalsIgnoreCase("recommended"))
            {
                // TODO: get reccomended forge version
            }
            else
            {
                // TODO: parse major and minor versions and stuff.
            }
        }
    }
}
