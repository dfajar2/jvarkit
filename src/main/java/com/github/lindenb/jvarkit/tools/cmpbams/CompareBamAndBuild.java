/*
The MIT License (MIT)

Copyright (c) 2016 Pierre Lindenbaum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


History:

*/
package com.github.lindenb.jvarkit.tools.cmpbams;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.github.lindenb.jvarkit.util.picard.AbstractDataCodec;
import com.github.lindenb.jvarkit.util.picard.IntervalUtils;
import com.github.lindenb.jvarkit.util.picard.SAMSequenceDictionaryProgress;
import com.github.lindenb.jvarkit.util.picard.SamFileReaderFactory;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.liftover.LiftOver;
import htsjdk.samtools.SAMFlag;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.SortingCollection;


public class CompareBamAndBuild  extends AbstractCompareBamAndBuild
	{
	private static final org.slf4j.Logger LOG = com.github.lindenb.jvarkit.util.log.Logging.getLog(CompareBamAndBuild.class);
	private final File bamFiles[]=new File[2];
	private SAMSequenceDictionary sequenceDictionaries[]=new SAMSequenceDictionary[2];
	private LiftOver liftOver=null;

	private class MatchComparator
		implements Comparator<Match>
		{
		@Override
		public int compare(final Match m0, final Match m1)
			{
			int i=m0.readName.compareTo(m1.readName);
			if(i!=0) return i;
			i=(int)m0.indexInPair()-(int)m1.indexInPair();
			if(i!=0) return i;
			if(m0.firstBamFile!=m1.firstBamFile) throw new IllegalStateException();
			i=m0.tid-m1.tid;
			if(i!=0) return i;
			i=m0.pos-m1.pos;
			if(i!=0) return i;
			return 0;
			}
		}
	
	private class MatchOrdererInSortingCollection
	implements Comparator<Match>
		{
		@Override
		public int compare(final Match m0, final Match m1)
			{
			int i=m0.readName.compareTo(m1.readName);
			if(i!=0) return i;
			i=(int)m0.indexInPair()-(int)m1.indexInPair();
			return i;
			}
		}
	
	private class MatchCodec
		extends AbstractDataCodec<Match>
		{
		@Override
		public MatchCodec clone()
			{
			return new MatchCodec();
			}
		@Override
		public Match decode(DataInputStream dis) throws IOException
			{
			final Match m=new Match();
			try {
				m.readName=dis.readUTF();
				}
			catch(IOException err)
				{
				return null;
				}
			m.firstBamFile=dis.readBoolean();
			m.tid=dis.readInt();
			m.pos=dis.readInt();
			m.flag=dis.readInt();
			return m;
			}
		@Override
		public void encode(DataOutputStream dos, Match match)
				throws IOException
			{
			dos.writeUTF(match.readName);
			dos.writeBoolean(match.firstBamFile);
			dos.writeInt(match.tid);
			dos.writeInt(match.pos);
			dos.writeInt(match.flag);
			}
		
		}
	
	private class Match
		{
		String readName;
		int flag=0;
		int tid=-1;
		boolean firstBamFile=true;
		int pos=-1;

		@Override
		public int hashCode()
			{
			int result = 1;
			result = 31 * result + flag;
			result = 31 * result + pos;
			result = 31 * result + tid;
			result = 31 * result + readName.hashCode();
			result = 31 * result + (firstBamFile?0:1);
			return result;
			}
		
		String getChrom()
			{	
			if(tid==-1) return null;
			return sequenceDictionaries[firstBamFile?0:1].getSequence(tid).getSequenceName();
			}
		
		int indexInPair() {
			if (!SAMFlag.READ_PAIRED.isSet(this.flag)) return 0;
			return SAMFlag.FIRST_OF_PAIR.isSet(this.flag)?1:2;
		}
		
		Interval getLiftOver()
			{
			if(tid==-1) return null;
			Interval src=new Interval(getChrom(),pos,pos);
			if(!firstBamFile) return src;
			return liftOver.liftOver(src);
			}
		
		@Override
		public boolean equals(final Object obj)
			{
			if (this == obj) { return true; }
			if (obj == null) { return false; }
			Match other = (Match) obj;
			if (indexInPair() != other.indexInPair()) { return false; }
			if (compareStrings(getChrom(), other.getChrom())!=0) { return false; }
			if(tid==-1) return true;
			if (pos != other.pos) { return false; }
			if (firstBamFile != other.firstBamFile) { return false; }
			if(!readName.equals(other.readName)) return false;
			return true;
			}
		}
	
	private int compareStrings(final String s1,final String s2)
		{
		if(s1==null)
			{
			return s2==null?0:-1;
			}
		if(s2==null) return 1;
		return 0;
		}
	
	/*
	private int compareTid(boolean is_first1,int tid1,boolean is_first2,int tid2)
		{
		if(tid1==-1)
			{
			return tid2==-1?0:-1;
			}
		if(tid2==-1)
			{
			return 1;
			}
		String chrom1=this.sequenceDictionaries[is_first1?0:1].getSequence(tid1).getSequenceName();
		String chrom2=this.sequenceDictionaries[is_first2?0:1].getSequence(tid2).getSequenceName();
		if(chrom1==null)
			{
			return chrom2==null?0:-1;
			}
		if(chrom2==null) return 1;
		return compare(chrom1,chrom2);
		}*/
	
	private void print(final PrintWriter out,final Set<Match> set)
		{
		boolean first=true;
		for(final Match m:set)
			{
			if(!first)out.print(',');
			first=false;
			if(m.tid<0){ out.print("unmapped"); continue;}
			if(m.firstBamFile)
				{
				out.print(String.valueOf(m.getChrom()+":"+m.pos+"->"));
				}
			
			final Interval interval=m.getLiftOver();
			if(interval==null)
				{
				out.print("liftoverfail");
				}
			else
				{
				out.print(String.valueOf(interval.getContig()+":"+interval.getStart()));
				}
			}
		if(first) out.print("(empty)");
		}
	
	
    private boolean same(final Set<Match> set1,final Set<Match> set2)
    	{
    	for(final Match m0:set1)
    		{
    		final Interval i0=m0.getLiftOver();
    		if(i0==null || i0.getContig()==null) continue;
    		for(final Match m1:set2)
	    		{
    			int i=m0.readName.compareTo(m1.readName);
    			if(i!=0) continue;
    			i=(int)m0.indexInPair()-(int)m1.indexInPair();
    			if(i!=0) continue;
    			//i= m0.bamIndex - m1.bamIndex;//NO ! (when comparing two Set<Match>)
    			//if(i!=0) return i;
    			final Interval i1=m1.getLiftOver();
        		if(i1==null || i1.getContig()==null) continue;
    			
    			i= compareStrings(i0.getContig(),i1.getContig());
    			if(i!=0) continue;
    			i= Math.abs(i0.getStart() -i1.getStart());
    			if(i>this.distance_tolerance) continue;
    			return true;
	    		}
    		}
    	return false;
    	}
   

	@Override
	public Collection<Throwable> call() throws Exception {
		PrintWriter out = null;
		SortingCollection<Match> database = null;
		if(super.chainFile==null) {
			return wrapException("Chain file is not defined Option -"+OPTION_CHAINFILE);
			}
		
		final List<String> args = super.getInputFiles();
		
		if(args.size()!=2)
			{
			return wrapException("Illegal number of arguments. Expected two indexed BAMS.");
			}
			
		try
				{
				LOG.info("load chain file");
				this.liftOver=new LiftOver(super.chainFile);
				database = SortingCollection.newInstance(
						Match.class,
						new MatchCodec(),
						new MatchOrdererInSortingCollection(),
						super.getMaxRecordsInRam(),
						super.getTmpDirectories()
						);
				
				database.setDestructiveIteration(true);
		
				
				for(int currentSamFileIndex=0;
						currentSamFileIndex<2;
						currentSamFileIndex++ )
					{
					final File samFile=new File(args.get(currentSamFileIndex));
					LOG.info("read "+samFile);
					this.bamFiles[currentSamFileIndex]=samFile;
					SamReader samFileReader=SamFileReaderFactory.mewInstance().open(samFile);
					final SAMSequenceDictionary dict=samFileReader.getFileHeader().getSequenceDictionary();
					this.sequenceDictionaries[currentSamFileIndex]=dict;
					if(dict.isEmpty())
						{
						samFileReader.close();
						return wrapException("Empty Dict  in "+samFile);
						}
					
				
					final Interval interval;
					if(REGION!=null)
						{
						interval=IntervalUtils.parseOne(dict, REGION);
						if(interval==null)
							{
							samFileReader.close();
							return wrapException("Cannot parse "+REGION+" (bad syntax or not in dictionary");
							}
						}
					else
						{
						interval = null;
						}
					
					final SAMRecordIterator iter;
					if(interval==null)
						{
						iter=samFileReader.iterator();
						}
					else
						{
						iter=samFileReader.queryOverlapping(interval.getContig(), interval.getStart(), interval.getEnd());
						}
					final SAMSequenceDictionaryProgress progress=new SAMSequenceDictionaryProgress(dict);
					while(iter.hasNext() )
						{
						final SAMRecord rec=progress.watch(iter.next());
						
						if(rec.isSecondaryOrSupplementary()) continue;
						
						final Match m=new Match();
						m.flag = rec.getFlags();
						m.readName = rec.getReadName();
						m.firstBamFile = currentSamFileIndex==0;
						if(rec.getReadUnmappedFlag())
							{
							m.tid=-1;
							m.pos=-1;
							}
						else
							{
							m.tid=rec.getReferenceIndex();
							m.pos=rec.getAlignmentStart();
							}
						database.add(m);
						}
					iter.close();
					samFileReader.close();
					samFileReader=null;
					LOG.info("Close "+samFile);
					}
				database.doneAdding();
				LOG.info("Writing results....");
				
				out = super.openFileOrStdoutAsPrintWriter();
				
				//compute the differences for each read
				out.print("#READ-Name\tCOMPARE");
				for(final File f:this.bamFiles)
					{
					out.print("\t"+f);
					}
				out.println();
				
				/* create an array of set<Match> */
				final MatchComparator match_comparator=new MatchComparator();
				final List<Set<Match>> matches=new ArrayList<Set<CompareBamAndBuild.Match>>(2);
				while(matches.size() < 2)
					{
					matches.add(new TreeSet<CompareBamAndBuild.Match>(match_comparator));
					}
				
				CloseableIterator<Match> iter=database.iterator();
				String currReadName=null;
				int curr_num_in_pair=-1;
				for(;;)
					{
					Match nextMatch = null;
					if(iter.hasNext())
						{
						nextMatch = iter.next();
						}
					if(nextMatch==null ||
						(currReadName!=null && !currReadName.equals(nextMatch.readName)) ||
						(curr_num_in_pair!=-1 && curr_num_in_pair!=nextMatch.indexInPair()))
						{
						if(currReadName!=null)
							{
							out.print(currReadName);
							if(curr_num_in_pair>0)
								{
								out.print("/");
								out.print(curr_num_in_pair);
								}
							out.print("\t");
							
							if(same(matches.get(0),matches.get(1)))
								{
								out.print("EQ");
								}
							else
								{
								out.print("NE");
								}
							
		
							for(int x=0;x<2;++x)
								{
								out.print("\t");
								print(out,matches.get(x));
								}
							
							out.println();
							}
						if(nextMatch==null) break;
						for(final Set<Match> set:matches) set.clear();
						}
					currReadName=nextMatch.readName;
					curr_num_in_pair=nextMatch.indexInPair();
					matches.get(nextMatch.firstBamFile?0:1).add(nextMatch);
					}
				
				iter.close();
				out.flush();
				out.close();
				database.cleanup();
				return RETURN_OK;
				}
			catch(final Exception err)
				{
				return wrapException(err);
				}
			finally
				{
				CloserUtil.close(out);
				this.liftOver=null;
				}
		}
	
	public static void main(String[] args) throws Exception
		{
		new CompareBamAndBuild().instanceMainWithExit(args);
		}
}
