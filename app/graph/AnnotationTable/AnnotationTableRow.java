package graph.AnnotationTable;

import com.antigenomics.vdjtools.Clonotype;

public class AnnotationTableRow {
    public Double freq;
    public Integer count;
    public String cdr3nt;
    public AnnotationTableCdr3aa cdr3aa;
    public String v;
    public String j;

    public AnnotationTableRow(Clonotype clonotype) {
        this.cdr3aa = new AnnotationTableCdr3aa(clonotype.getCdr3aa(), -1, clonotype.getVEnd(), clonotype.getJStart(), clonotype.getDStart(), clonotype.getDEnd());
        this.freq = clonotype.getFreq();
        this.count = clonotype.getCount();
        this.v = clonotype.getV();
        this.j = clonotype.getJ();
        this.cdr3nt = clonotype.getCdr3nt();
    }
}
