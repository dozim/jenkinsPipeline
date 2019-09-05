def RELEASE = 'RELEASES'
def SNAPSHOTS = 'SNAPSHOTS'
def server =  Artifactory.server 'ART'
def maven = Artifactory.newMavenBuild()
def info =  Artifactory.newBuildInfo()
def params = []

node {
    params += choice(choices: [SNAPSHOTS , RELEASE], description: 'Choose Build Type', name: 'Build Master/Release')
    properties([parameters(params)])


    stage ('configure') {
        info.env.capture = true
        //info.retention maxDays: 30 // only commercial

        maven.tool = 'maven3.6'
        // maven.opts = '-Xms1024m -Xmx4096m'
        maven.deployer server: server, releaseRepo: RELEASE, snapshotRepo: SNAPSHOTS
        maven.deployer.deployArtifacts = false

        echo "Configuration Done.\n Maven Tool:${maven.tool}"
    }
    stage ('clean') {
        deleteDir()
    }
    stage('checkout') {
        git 'https://github.com/dozim/helloJenkins.git'
    }
    stage('Print Commit Hash') {
        def commitHash = checkout(scm).GIT_COMMIT
        echo commitHash
    }
    stage('compile') {
        maven.run pom: 'pom.xml', goals: '-B compile', buildInfo: info
    }
    stage('test') {
        maven.run pom: 'pom.xml', goals: '-B test', buildInfo: info
    }
    stage('package') {
        maven.run pom: 'pom.xml', goals: '-B package -DskipTests=true', buildInfo: info
    }
    stage('Echo Release Version') {
        def pom = readMavenPom()
        String releaseVersion = generateReleaseVersionFromPom(pom)
        echo releaseVersion
    }
    stage('publish') {
        maven.deployer.deployArtifacts = true
        maven.deployer.deployArtifacts info
        server.publishBuildInfo info
        maven.deployer.deployArtifacts = false
    }
    stage ('clean after build') {
        cleanWs deleteDirs: true, notFailBuild: true
    }
}

static String generateReleaseVersionFromPom(pom) {
    def releaseVersion
    def releasepattern = ~/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})-\S{8}$/
    if (pom.version =~ /SNAPSHOT$/) {
        releaseVersion = pom.version.replace(/-SNAPSHOT/, '')
    } else {
        releaseVersion = pom.version.replaceFirst(releasepattern) { group -> "${group[1]}.${group[2]}.${(group[3] as int) + 1}" }
    }
    return releaseVersion
}