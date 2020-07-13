# Mirror Pool

![](res/image.png)
*Artwork by dm29 
(source: [here](https://www.deviantart.com/dm29/art/Second-Starlight-829599000)).*

Mirror Pool is a command line script designed for use on any JVM-compliant
platform, for downloading entire or partial clones of Derpibooru's image
gallery. You will need [Leiningen](https://leiningen.org/) installed to 
use the program.

## Using Mirror Pool

The default options will pull a full clone of Derpibooru, all you need to
supply is an API key which you can get 
[here](https://derpibooru.org/registration/edit):

```
$ lein run -- -k YOUR_KEY_HERE
```

### Specifying a data directory

By default, Mirror Pool saves data to a Crux database in `db/`, but you
might prefer to use another directory:

```
$ lein run -- -k YOUR_KEY_HERE -d data
```

### Specifying an image directory

By default, Mirror Pool saves images to `img/`, but you might prefer a
different location:

```
$ lein run -- -k YOUR_KEY_HERE -i ~/pictures/pony
```

### Specifying tags to download

As already mentioned, Mirror Pool defaults to downloading an entire copy
of Derpibooru, but you might just want to get pictures of best pony:

```
$ lein run -- -k YOUR_KEY_HERE -q "lightning dust"
```

### Restarting the mirror

By default, Mirror Pool remembers which pages you have already downloaded,
which allows for just downloading newer images and ignoring the ones you
already have. If you need to restart your downloads from the first image:

```
$ lein run -- -k YOUR_KEY_HERE --restart
```

### Getting more information

There are several levels of verbosity available through `-v`, `-vv`, and
`-vvv`. These are only recommended for debugging.

## To Do

- [x] Support query instances with resume function (this is pretty important);
- [ ] Support back-ends which aren't Crux;
- [ ] Make the uberjar work.

## Why Mirror Pool?

Unfortunately, no one is safe from Cancel Culture and our beloved archive
site has been [targeted by the hate mob](https://archive.is/yaG2p). I do
not have the confidence in TSP and the staff team to resist attempts to
censor content, which could leave some art lost in the annals of time.

## Can I support you?

Thanks for the gesture, but I'm fine. If you feel an obligation to give
back, please consider making a donation to one of the following free
speech charities:

- [The Comic Book Legal Defense Fund](http://cbldf.org/)
- [Index on Censorship](https://www.indexoncensorship.org/)
- [The Orwell Foundation](https://www.orwellfoundation.com/)
- [The Cato Institute](https://www.cato.org)

Having said that, gift art is always welcome. :smile:
