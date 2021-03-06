package org.janelia.workstation.mesh_loader;

import java.util.Arrays;

/**
 * Created by fosterl on 4/3/14.
 */
public class VertexInfoKey {
    private double[] position;

    public double[] getPosition() {
        return position;
    }

    public void setPosition( double[] position ) {
        this.position = position;
    }

    public boolean equals( Object o ) {
        boolean rtnVal = false;
        if ( o != null  &&  o instanceof VertexInfoKey ) {
            VertexInfoKey otherKey = (VertexInfoKey)o;
            rtnVal = Arrays.equals( otherKey.getPosition(), position );
        }
        return rtnVal;
    }

    public String toString() {
        StringBuilder rtnBldr = new StringBuilder();
        for ( double posElement: position ) {
            rtnBldr.append(" ").append( posElement );
        }
        return rtnBldr.toString().trim();
    }

    public int hashCode() {
        return Arrays.hashCode( position );
    }
}
