package one.papachi.webfs;

import one.papachi.dokany4j.DokanOptions;
import one.papachi.dokany4j.Dokany4j;
import one.papachi.fuse4j.FuseOptions;
import one.papachi.vfs4j.VirtualFileSystem;
import one.papachi.vfs4j.dokany.DokanyFileSystem;
import one.papachi.vfs4j.fuse.FuseFileSystem;
import one.papachi.vfs4j.macfuse.MacFuseFileSystem;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WebFileSystem implements VirtualFileSystem {

	public static void main(String[] args) {
		List<String> argsList = Arrays.asList(args);
		if (argsList.contains("--help") || argsList.stream().noneMatch(a -> a.startsWith("--src=")) || argsList.stream().noneMatch(a -> a.startsWith("--dst=")) || argsList.stream().noneMatch(a -> a.startsWith("--url=")) || argsList.stream().noneMatch(a -> a.startsWith("--secret="))) {
			System.out.println(
					"""
                    Usage:
                    --help            usage
                    --debug           turn on debug mode in dokany
                    --src=<path>      source path (path on the remote server)
                    --dst=<path>      destination path (mount point)
                    --url=<url>       base url where rfs.php is located
                    --secret=<secret> shared secret to use for authentication
                    
                    Example:
                    Windows:
                    java -jar rfs.jar --src=./ --dst=W: --url=http://localhost/rfs.php --secret=password
                    Linux:
                    java -jar rfs.jar --src=./ --dst=./wfs --url=http://localhost/rfs.php --secret=password
                    macOS:
                    java -jar rfs.jar --src=./ --dst=./wfs --url=http://localhost/rfs.php --secret=password
                    """);
			return;
		}
		String src = argsList.stream().filter(a -> a.startsWith("--src=")).map(a -> a.substring("--src=".length())).findFirst().orElse(null);
		String dst = argsList.stream().filter(a -> a.startsWith("--dst=")).map(a -> a.substring("--dst=".length())).findFirst().orElse(null);
		String url = argsList.stream().filter(a -> a.startsWith("--url=")).map(a -> a.substring("--url=".length())).findFirst().orElse(null);
		String secret = argsList.stream().filter(a -> a.startsWith("--secret=")).map(a -> a.substring("--secret=".length())).findFirst().orElse(null);
		WebFileSystem webfs = new WebFileSystem(src, url, secret);
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.contains("win")) {
			DokanyFileSystem dokanyFileSystem = new DokanyFileSystem(webfs);
			Runtime.getRuntime().addShutdownHook(new Thread(() -> Dokany4j.unmount(dst)));
			DokanOptions.Builder builder = new DokanOptions.Builder();
			if (argsList.contains("--debug"))
				builder.setDokanOptionDebug().setDokanOptionStderr();
			int dokanOptions = builder.build().dokanOptions();
			dokanyFileSystem.mount(205, true, dokanOptions, dst, "", 120, 4096, 4096);
		} else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
			FuseFileSystem fuseFileSystem = new FuseFileSystem(webfs);
			FuseOptions fuseOptions = new FuseOptions().setForegroundOperation().setAutoUnmount().setMountPoint(dst);
			if (argsList.contains("--debug"))
				fuseOptions.setDebug();
			String[] fuseArgs = fuseOptions.build();
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					Runtime.getRuntime().exec(new String[] {"fusermount", "-u", dst}).waitFor();
				} catch (IOException | InterruptedException ignored) {
				}
			}));
			fuseFileSystem.mount(fuseArgs);
		} else if (osName.contains("mac")) {
			MacFuseFileSystem fuseFileSystem = new MacFuseFileSystem(webfs);
			FuseOptions fuseOptions = new FuseOptions().setForegroundOperation().setAutoUnmount().setMountPoint(dst);
			if (argsList.contains("--debug"))
				fuseOptions.setDebug();
			String[] fuseArgs = fuseOptions.build();
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					Runtime.getRuntime().exec(new String[] {"umount", "-f", dst}).waitFor();
				} catch (IOException | InterruptedException ignored) {
				}
			}));
			fuseFileSystem.mount(fuseArgs);
		}
	}

	private final HttpClient httpClient;

	private final String src, url, secret;

	private static URI uriBuilder(String url, Map<String, Object> parameters) {
		String query = parameters.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue().toString(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
		return URI.create(url + "?" + query);
	}

	private static FileInfo getFileInfo(String s) {
		String[] split = s.split(";");
		String name = split[0];
		boolean isDir = Boolean.parseBoolean(split[1]);
		long size = Long.parseLong(split[2]);
		long atime = Long.parseLong(split[3]) * 1000L;
		long mtime = Long.parseLong(split[4]) * 1000L;
		long ctime = Long.parseLong(split[5]) * 1000L;
		return new FileInfo(name, isDir, size, 0, 0, atime, mtime, ctime);
	}

	public WebFileSystem(String src, String url, String secret) {
		this.src = src;
		this.url = url;
		this.secret = secret;
		httpClient = HttpClient.newBuilder().build();
	}

	@Override
	public List<VirtualFileSystem.FileInfo> listDirectory(String filename) throws IOException {
		try {
			String path = src + filename;
			HttpRequest httpRequest = HttpRequest.newBuilder(uriBuilder(url, Map.of("s", secret, "p", path, "f", "di"))).GET().build();
			HttpResponse<Stream<String>> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
			if (httpResponse.statusCode() != 200)
				throw new IOException();
			return httpResponse.body().map(WebFileSystem::getFileInfo).toList();
		} catch (Exception e) {
			throw new IOException();
		}
	}

	@Override
	public VirtualFileSystem.FileInfo listFile(String filename) throws IOException {
		try {
			String path = src + filename;
			HttpRequest httpRequest = HttpRequest.newBuilder(uriBuilder(url, Map.of("s", secret, "p", path, "f", "fi"))).GET().build();
			HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (httpResponse.statusCode() != 200)
				throw new IOException();
			return getFileInfo(httpResponse.body());
		} catch (Exception e) {
			throw new IOException();
		}
	}

	@Override
	public boolean isDirectoryEmpty(String filename) throws IOException {
		try {
			String path = src + filename;
			HttpRequest httpRequest = HttpRequest.newBuilder(uriBuilder(url, Map.of("s", secret, "p", path, "f", "nl"))).GET().build();
			HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (httpResponse.statusCode() != 200)
				throw new IOException();
			String s = httpResponse.body();
			return Boolean.parseBoolean(s);
		} catch (Exception e) {
			throw new IOException();
		}
	}

	@Override
	public void createDirectory(String filename) throws IOException {
		try {
			String path = src + filename;
			HttpRequest httpRequest = HttpRequest.newBuilder(uriBuilder(url, Map.of("s", secret, "p", path, "f", "md"))).POST(HttpRequest.BodyPublishers.noBody()).build();
			HttpResponse<Void> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
			if (httpResponse.statusCode() != 200)
				throw new IOException();
		} catch (Exception e) {
			throw new IOException();
		}
	}

	@Override
	public void createRegularFile(String filename) throws IOException {
		try {
			String path = src + filename;
			HttpRequest httpRequest = HttpRequest.newBuilder(uriBuilder(url, Map.of("s", secret, "p", path, "f", "mf"))).POST(HttpRequest.BodyPublishers.noBody()).build();
			HttpResponse<Void> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
			if (httpResponse.statusCode() != 200)
				throw new IOException();
		} catch (Exception e) {
			throw new IOException();
		}
	}

	@Override
	public void renameFile(String filename, String newFilename) throws IOException {
		try {
			String path = src + filename;
			String pathNew = src + newFilename;
			HttpRequest httpRequest = HttpRequest.newBuilder(uriBuilder(url, Map.of("s", secret, "p", path, "f", "rf", "n", pathNew))).POST(HttpRequest.BodyPublishers.noBody()).build();
			HttpResponse<Void> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
			if (httpResponse.statusCode() != 200)
				throw new IOException();
		} catch (Exception e) {
			throw new IOException();
		}
	}

	@Override
	public void deleteFile(String filename) throws IOException {
		try {
			String path = src + filename;
			HttpRequest httpRequest = HttpRequest.newBuilder(uriBuilder(url, Map.of("s", secret, "p", path, "f", "df"))).DELETE().build();
			HttpResponse<Void> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
			if (httpResponse.statusCode() != 200)
				throw new IOException();
		} catch (Exception e) {
			throw new IOException();
		}
	}

	@Override
	public void openFile(String filename, boolean write) {
		// no-op
	}

	@Override
	public void closeFile(String filename) {
		// no-op
	}

	@Override
	public int readFile(String filename, ByteBuffer buffer, long position) throws IOException {
		try {
			String path = src + filename;
			HttpRequest httpRequest = HttpRequest.newBuilder(uriBuilder(url, Map.of("s", secret, "p", path, "f", "rf", "o", position, "l", buffer.remaining()))).GET().build();
			HttpResponse<byte[]> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
			if (httpResponse.statusCode() != 200 && httpResponse.statusCode() != 201)
				throw new IOException();
			byte[] body = httpResponse.body();
			buffer.put(body);
			return body.length == 0 ? -1 : body.length;
		} catch (Exception e) {
			throw new IOException();
		}
	}

	@Override
	public int writeFile(String filename, ByteBuffer buffer, long position) throws IOException {
		try {
			String path = src + filename;
			byte[] data = new byte[buffer.remaining()];
			buffer.get(data);
			HttpRequest httpRequest = HttpRequest.newBuilder(uriBuilder(url, Map.of("s", secret, "p", path, "f", "wf", "o", position, "l", buffer.remaining()))).PUT(HttpRequest.BodyPublishers.ofByteArray(data)).build();
			HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (httpResponse.statusCode() != 200)
				throw new IOException();
			return Integer.parseInt(httpResponse.body());
		} catch (Exception e) {
			throw new IOException();
		}
	}

	@Override
	public void setFileSize(String filename, long size) throws IOException {
		try {
			String path = src + filename;
			HttpRequest httpRequest = HttpRequest.newBuilder(uriBuilder(url, Map.of("s", secret, "p", path, "f", "sf", "l", size))).POST(HttpRequest.BodyPublishers.noBody()).build();
			HttpResponse<Void> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
			if (httpResponse.statusCode() != 200)
				throw new IOException();
		} catch (Exception e) {
			throw new IOException();
		}
	}

}
