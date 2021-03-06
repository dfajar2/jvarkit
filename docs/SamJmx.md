# SamJmx

Monitor/interrupt/break a BAM/SAM stream with java JMX http://www.oracle.com/technetwork/articles/java/javamanagement-140525.html


## Usage

```
Usage: samjmx [options] Files
  Options:
    --bamcompression
      Compression Level.
      Default: 5
    -h, --help
      print help and exit
    --helpFormat
      What kind of help
      Possible Values: [usage, markdown, xml]
    -o, --out
      Output file. Optional . Default: stdout
    --samoutputformat
      Sam output format.
      Default: SAM
      Possible Values: [BAM, SAM, CRAM]
    --version
      print version and exit
    -p
      Stream identifier

```


## Keywords

 * sam
 * bam
 * jmx
 * monitoring


## Compilation

### Requirements / Dependencies

* java [compiler SDK 1.8](http://www.oracle.com/technetwork/java/index.html) (**NOT the old java 1.7 or 1.6**) and avoid OpenJdk, use the java from Oracle. Please check that this java is in the `${PATH}`. Setting JAVA_HOME is not enough : (e.g: https://github.com/lindenb/jvarkit/issues/23 )
* GNU Make >= 3.81
* curl/wget
* git
* xsltproc http://xmlsoft.org/XSLT/xsltproc2.html (tested with "libxml 20706, libxslt 10126 and libexslt 815")


### Download and Compile

```bash
$ git clone "https://github.com/lindenb/jvarkit.git"
$ cd jvarkit
$ make samjmx
```

The *.jar libraries are not included in the main jar file, [so you shouldn't move them](https://github.com/lindenb/jvarkit/issues/15#issuecomment-140099011 ).
The required libraries will be downloaded and installed in the `dist` directory.

Experimental: you can also create a [fat jar](https://stackoverflow.com/questions/19150811/) which contains classes from all the libraries, on which your project depends (it's bigger). Those fat-jar are generated by adding `standalone=yes` to the gnu make command, for example ` make samjmx standalone=yes`.

### edit 'local.mk' (optional)

The a file **local.mk** can be created edited to override/add some definitions.

For example it can be used to set the HTTP proxy:

```
http.proxy.host=your.host.com
http.proxy.port=124567
```
## Source code 

[https://github.com/lindenb/jvarkit/tree/master/src/main/java/com/github/lindenb/jvarkit/tools/jmx/SamJmx.java](https://github.com/lindenb/jvarkit/tree/master/src/main/java/com/github/lindenb/jvarkit/tools/jmx/SamJmx.java)


<details>
<summary>Git History</summary>

```
Mon May 29 12:33:45 2017 +0200 ; cont ; https://github.com/lindenb/jvarkit/commit/870be8e90d7e98d947f73e67ef9965f12f351846
Sun May 21 20:02:10 2017 +0200 ; instanceMain -> instanceMainWithExit ; https://github.com/lindenb/jvarkit/commit/4fa41d198fe7e063c92bdedc333cbcdd2b8240aa
Thu May 4 14:27:36 2017 +0200 ; fix make ; https://github.com/lindenb/jvarkit/commit/044933b2091b68f3241c60c85de5dd605ae02756
Mon May 1 15:40:19 2017 +0200 ; cont ; https://github.com/lindenb/jvarkit/commit/11aa7fcf4cc15aecc2cc2019fc2df8752731a278
Fri Jul 17 12:26:30 2015 +0200 ; all sample in shuffle ; https://github.com/lindenb/jvarkit/commit/fae29109c7b3e24a7dc9329d7d4eea06ee95a0f7
Fri Jul 10 14:18:14 2015 +0200 ; vcfjmx + samjmx tools to monitor/break ngs stream with jmx #tweet ; https://github.com/lindenb/jvarkit/commit/c41af20479e5733369e3445f32968e7c2ddb2e06
```

</details>

## Contribute

- Issue Tracker: [http://github.com/lindenb/jvarkit/issues](http://github.com/lindenb/jvarkit/issues)
- Source Code: [http://github.com/lindenb/jvarkit](http://github.com/lindenb/jvarkit)

## License

The project is licensed under the MIT license.

## Citing

Should you cite **samjmx** ? [https://github.com/mr-c/shouldacite/blob/master/should-I-cite-this-software.md](https://github.com/mr-c/shouldacite/blob/master/should-I-cite-this-software.md)

The current reference is:

[http://dx.doi.org/10.6084/m9.figshare.1425030](http://dx.doi.org/10.6084/m9.figshare.1425030)

> Lindenbaum, Pierre (2015): JVarkit: java-based utilities for Bioinformatics. figshare.
> [http://dx.doi.org/10.6084/m9.figshare.1425030](http://dx.doi.org/10.6084/m9.figshare.1425030)


## Example

```bash
$   java -jar dist/samjmx.jar  -T bam -p MyWorkflow1 input.bam > /dev/null
```

while the stream is running, open a new jconsole https://docs.oracle.com/javase/7/docs/technotes/guides/management/jconsole.html . here you can get the number of records, t
he elapsed time. Two operation are available:

* doBreak: interrupt current streaming , exit with success (0)
* doAbort: interrupt current streaming , exit with failure (-1)




